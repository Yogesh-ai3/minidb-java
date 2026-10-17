
package minidb.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Lexer {

    private static final Map<String, TokenType> KEYWORDS =
            Map.ofEntries(
                    Map.entry("CREATE", TokenType.CREATE),
                    Map.entry("DATABASE", TokenType.DATABASE),
                    Map.entry("USE", TokenType.USE),
                    Map.entry("TABLE", TokenType.TABLE),
                    Map.entry("INT", TokenType.INT),
                    Map.entry("VARCHAR", TokenType.VARCHAR),
                    Map.entry("PRIMARY", TokenType.PRIMARY),
                    Map.entry("KEY", TokenType.KEY),
                    Map.entry("INSERT", TokenType.INSERT),
                    Map.entry("INTO", TokenType.INTO),
                    Map.entry("VALUES", TokenType.VALUES),
                    Map.entry("SELECT", TokenType.SELECT),
                    Map.entry("FROM", TokenType.FROM),
                    Map.entry("WHERE", TokenType.WHERE),
                    Map.entry("UPDATE", TokenType.UPDATE),
                    Map.entry("SET", TokenType.SET),
                    Map.entry("DELETE", TokenType.DELETE)
            );

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start;
    private int current;
    private int line = 1;
    private int column = 1;
    private int startLine = 1;
    private int startColumn = 1;

    public Lexer(String source) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "SQL input cannot be null"
            );
        }

        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            startLine = line;
            startColumn = column;
            scanToken();
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return List.copyOf(tokens);
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            // Ignore whitespace
            case ' ', '\r', '\t', '\n' -> {
            }

            // Punctuation
            case '(' -> addToken(TokenType.LEFT_PAREN);
            case ')' -> addToken(TokenType.RIGHT_PAREN);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case '*' -> addToken(TokenType.STAR);
            case ';' -> addToken(TokenType.SEMICOLON);

            // Operators
            case '=' -> addToken(TokenType.EQUAL);
            case '>' -> addToken(
                    match('=')
                            ? TokenType.GREATER_EQUAL
                            : TokenType.GREATER_THAN
            );
            case '<' -> addToken(
                    match('=')
                            ? TokenType.LESS_EQUAL
                            : TokenType.LESS_THAN
            );
            case '!' -> {
                if (match('=')) {
                    addToken(TokenType.NOT_EQUAL);
                } else {
                    throw error("Expected '=' after '!'");
                }
            }

            // Literals
            case '\'' -> string();

            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    throw error(
                            "Unexpected character '" + c + "'"
                    );
                }
            }
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }

        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(
                text.toUpperCase(Locale.ROOT),
                TokenType.IDENTIFIER
        );

        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) {
            advance();
        }

        addToken(TokenType.INTEGER_LITERAL);
    }

    private void string() {
        while (!isAtEnd()) {
            if (peek() == '\n' || peek() == '\r') {
                throw error(
                        "String literal cannot contain a newline"
                );
            }

            if (peek() == '\'') {
                // SQL escapes a single quote by doubling it:
                // 'O''Brien' represents O'Brien.
                if (peekNext() == '\'') {
                    advance();
                    advance();
                } else {
                    advance(); // Closing quote
                    addToken(TokenType.STRING_LITERAL);
                    return;
                }
            } else {
                advance();
            }
        }

        throw error("Unterminated string literal");
    }

    private char advance() {
        char c = source.charAt(current++);

        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }

        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }

        advance();
        return true;
    }

    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }

        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }

        return source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void addToken(TokenType type) {
        String lexeme = source.substring(start, current);

        tokens.add(new Token(
                type,
                lexeme,
                startLine,
                startColumn
        ));
    }

    private LexerException error(String message) {
        return new LexerException(
                message + " at line " + startLine
                        + ", column " + startColumn
        );
    }
}
