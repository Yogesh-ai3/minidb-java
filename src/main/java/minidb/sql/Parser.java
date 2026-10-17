
package minidb.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Parser {

    private final List<Token> tokens;
    private int current;

    public Parser(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "Token list cannot be null or empty"
            );
        }

        this.tokens = List.copyOf(tokens);

        if (this.tokens.get(this.tokens.size() - 1).type()
                != TokenType.EOF) {
            throw new IllegalArgumentException(
                    "Token list must end with EOF"
            );
        }
    }

    public SqlCommand parse() {
        if (check(TokenType.EOF)) {
            throw error(peek(), "Expected a SQL statement");
        }

        SqlCommand command = statement();

        // A semicolon is optional, but only one statement
        // is accepted per parse() call.
        match(TokenType.SEMICOLON);
        consume(TokenType.EOF, "Unexpected tokens after statement");

        return command;
    }

    private SqlCommand statement() {
        if (match(TokenType.CREATE)) {
            return createStatement();
        }

        if (match(TokenType.USE)) {
            return useDatabase();
        }

        if (match(TokenType.INSERT)) {
            return insert();
        }

        if (match(TokenType.SELECT)) {
            return select();
        }

        if (match(TokenType.UPDATE)) {
            return update();
        }

        if (match(TokenType.DELETE)) {
            return delete();
        }

        throw error(peek(), "Expected a supported SQL statement");
    }

    private SqlCommand createStatement() {
        if (match(TokenType.DATABASE)) {
            return new SqlCommand.CreateDatabase(
                    consume(
                            TokenType.IDENTIFIER,
                            "Expected database name"
                    ).lexeme()
            );
        }

        if (match(TokenType.TABLE)) {
            return createTable();
        }

        throw error(
                peek(),
                "Expected DATABASE or TABLE after CREATE"
        );
    }

    private SqlCommand.UseDatabase useDatabase() {
        String name = consume(
                TokenType.IDENTIFIER,
                "Expected database name after USE"
        ).lexeme();

        return new SqlCommand.UseDatabase(name);
    }

    private SqlCommand.CreateTable createTable() {
        String tableName = consume(
                TokenType.IDENTIFIER,
                "Expected table name"
        ).lexeme();

        consume(TokenType.LEFT_PAREN, "Expected '(' after table name");

        List<SqlCommand.ColumnDefinition> columns =
                new ArrayList<>();

        do {
            columns.add(columnDefinition());
        } while (match(TokenType.COMMA));

        consume(TokenType.RIGHT_PAREN, "Expected ')' after columns");

        return new SqlCommand.CreateTable(tableName, columns);
    }

    private SqlCommand.ColumnDefinition columnDefinition() {
        String name = consume(
                TokenType.IDENTIFIER,
                "Expected column name"
        ).lexeme();

        SqlCommand.DataType dataType;
        Integer varcharLength = null;

        if (match(TokenType.INT)) {
            dataType = SqlCommand.DataType.INT;
        } else if (match(TokenType.VARCHAR)) {
            dataType = SqlCommand.DataType.VARCHAR;

            consume(
                    TokenType.LEFT_PAREN,
                    "Expected '(' after VARCHAR"
            );

            Token lengthToken = consume(
                    TokenType.INTEGER_LITERAL,
                    "Expected VARCHAR length"
            );

            long length = parseInteger(lengthToken);

            if (length <= 0 || length > Integer.MAX_VALUE) {
                throw error(
                        lengthToken,
                        "VARCHAR length must be a positive integer"
                );
            }

            varcharLength = (int) length;

            consume(
                    TokenType.RIGHT_PAREN,
                    "Expected ')' after VARCHAR length"
            );
        } else {
            throw error(
                    peek(),
                    "Expected INT or VARCHAR for column type"
            );
        }

        boolean primaryKey = false;

        if (match(TokenType.PRIMARY)) {
            consume(TokenType.KEY, "Expected KEY after PRIMARY");
            primaryKey = true;
        }

        try {
            return new SqlCommand.ColumnDefinition(
                    name,
                    dataType,
                    varcharLength,
                    primaryKey
            );
        } catch (IllegalArgumentException exception) {
            throw error(peek(), exception.getMessage());
        }
    }

    private SqlCommand.Insert insert() {
        consume(TokenType.INTO, "Expected INTO after INSERT");

        String tableName = consume(
                TokenType.IDENTIFIER,
                "Expected table name after INTO"
        ).lexeme();

        consume(TokenType.VALUES, "Expected VALUES");
        consume(TokenType.LEFT_PAREN, "Expected '(' after VALUES");

        List<SqlCommand.SqlValue> values = new ArrayList<>();
        do {
            values.add(value());
        } while (match(TokenType.COMMA));

        consume(TokenType.RIGHT_PAREN, "Expected ')' after values");

        return new SqlCommand.Insert(tableName, values);
    }

    private SqlCommand.Select select() {
        boolean selectAll = match(TokenType.STAR);
        List<String> columns = new ArrayList<>();

        if (!selectAll) {
            columns.add(consume(
                    TokenType.IDENTIFIER,
                    "Expected '*' or a column name after SELECT"
            ).lexeme());

            while (match(TokenType.COMMA)) {
                columns.add(consume(
                        TokenType.IDENTIFIER,
                        "Expected column name after ','"
                ).lexeme());
            }
        }

        consume(TokenType.FROM, "Expected FROM after selected columns");

        String tableName = consume(
                TokenType.IDENTIFIER,
                "Expected table name after FROM"
        ).lexeme();

        Optional<SqlCommand.Condition> condition =
                Optional.empty();

        if (match(TokenType.WHERE)) {
            condition = Optional.of(condition());
        }

        return new SqlCommand.Select(
                selectAll, columns, tableName, condition
        );
    }

    private SqlCommand.Update update() {
        String tableName = consume(
                TokenType.IDENTIFIER,
                "Expected table name after UPDATE"
        ).lexeme();

        consume(TokenType.SET, "Expected SET after table name");

        String columnName = consume(
                TokenType.IDENTIFIER,
                "Expected column name after SET"
        ).lexeme();

        consume(TokenType.EQUAL, "Expected '=' in assignment");

        SqlCommand.SqlValue value = value();

        Optional<SqlCommand.Condition> condition =
                Optional.empty();

        if (match(TokenType.WHERE)) {
            condition = Optional.of(condition());
        }

        return new SqlCommand.Update(
                tableName, columnName, value, condition
        );
    }

    private SqlCommand.Delete delete() {
        consume(TokenType.FROM, "Expected FROM after DELETE");

        String tableName = consume(
                TokenType.IDENTIFIER,
                "Expected table name after FROM"
        ).lexeme();

        Optional<SqlCommand.Condition> condition =
                Optional.empty();

        if (match(TokenType.WHERE)) {
            condition = Optional.of(condition());
        }

        return new SqlCommand.Delete(tableName, condition);
    }

    private SqlCommand.Condition condition() {
        String columnName = consume(
                TokenType.IDENTIFIER,
                "Expected column name in WHERE condition"
        ).lexeme();

        SqlCommand.ComparisonOperator operator;

        if (match(TokenType.EQUAL)) {
            operator = SqlCommand.ComparisonOperator.EQUAL;
        } else if (match(TokenType.NOT_EQUAL)) {
            operator = SqlCommand.ComparisonOperator.NOT_EQUAL;
        } else if (match(TokenType.GREATER_THAN)) {
            operator = SqlCommand.ComparisonOperator.GREATER_THAN;
        } else if (match(TokenType.LESS_THAN)) {
            operator = SqlCommand.ComparisonOperator.LESS_THAN;
        } else if (match(TokenType.GREATER_EQUAL)) {
            operator = SqlCommand.ComparisonOperator.GREATER_EQUAL;
        } else if (match(TokenType.LESS_EQUAL)) {
            operator = SqlCommand.ComparisonOperator.LESS_EQUAL;
        } else {
            throw error(
                    peek(),
                    "Expected comparison operator in WHERE condition"
            );
        }

        return new SqlCommand.Condition(
                columnName, operator, value()
        );
    }

    private SqlCommand.SqlValue value() {
        if (match(TokenType.INTEGER_LITERAL)) {
            return new SqlCommand.IntegerValue(
                    parseInteger(previous())
            );
        }

        if (match(TokenType.STRING_LITERAL)) {
            String lexeme = previous().lexeme();

            // Remove the surrounding quotes and convert doubled
            // SQL quotes (O''Brien) into a single quote.
            String text = lexeme
                    .substring(1, lexeme.length() - 1)
                    .replace("''", "'");

            return new SqlCommand.StringValue(text);
        }

        throw error(
                peek(),
                "Expected an integer or string literal"
        );
    }

    private long parseInteger(Token token) {
        try {
            return Long.parseLong(token.lexeme());
        } catch (NumberFormatException exception) {
            throw error(
                    token,
                    "Integer literal is outside the supported range"
            );
        }
    }

    private boolean match(TokenType type) {
        if (!check(type)) {
            return false;
        }

        advance();
        return true;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token advance() {
        if (!check(TokenType.EOF)) {
            current++;
        }

        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParserException error(Token token, String message) {
        return new ParserException(
                message + " at line " + token.line()
                        + ", column " + token.column()
        );
    }
}
