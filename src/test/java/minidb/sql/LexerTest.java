
package minidb.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    private List<Token> scan(String sql) {
        return new Lexer(sql).scanTokens();
    }

    @Test
    void tokenizesCreateTableStatement() {
        List<Token> tokens = scan(
                "CREATE TABLE students "
                        + "(id INT PRIMARY KEY, age INT);"
        );

        assertEquals(
                List.of(
                        TokenType.CREATE,
                        TokenType.TABLE,
                        TokenType.IDENTIFIER,
                        TokenType.LEFT_PAREN,
                        TokenType.IDENTIFIER,
                        TokenType.INT,
                        TokenType.PRIMARY,
                        TokenType.KEY,
                        TokenType.COMMA,
                        TokenType.IDENTIFIER,
                        TokenType.INT,
                        TokenType.RIGHT_PAREN,
                        TokenType.SEMICOLON,
                        TokenType.EOF
                ),
                tokens.stream().map(Token::type).toList()
        );

        assertEquals("students", tokens.get(2).lexeme());
        assertEquals(1, tokens.get(2).line());
        assertEquals(14, tokens.get(2).column());
    }

    @Test
    void recognizesKeywordsWithoutCaseSensitivity() {
        List<Token> tokens = scan("select Name FROM students");

        assertEquals(TokenType.SELECT, tokens.get(0).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("Name", tokens.get(1).lexeme());
        assertEquals(TokenType.FROM, tokens.get(2).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
    }

    @Test
    void recognizesComparisonOperators() {
        List<Token> tokens = scan(">= <= != > < =");

        assertEquals(
                List.of(
                        TokenType.GREATER_EQUAL,
                        TokenType.LESS_EQUAL,
                        TokenType.NOT_EQUAL,
                        TokenType.GREATER_THAN,
                        TokenType.LESS_THAN,
                        TokenType.EQUAL,
                        TokenType.EOF
                ),
                tokens.stream().map(Token::type).toList()
        );
    }

    @Test
    void recognizesIntegerAndStringLiterals() {
        List<Token> tokens = scan("42 'Alice' 'O''Brien'");

        assertEquals(TokenType.INTEGER_LITERAL,
                tokens.get(0).type());
        assertEquals("42", tokens.get(0).lexeme());

        assertEquals(TokenType.STRING_LITERAL,
                tokens.get(1).type());
        assertEquals("'Alice'", tokens.get(1).lexeme());

        assertEquals(TokenType.STRING_LITERAL,
                tokens.get(2).type());
        assertEquals("'O''Brien'", tokens.get(2).lexeme());
    }

    @Test
    void rejectsUnexpectedCharacters() {
        LexerException exception = assertThrows(
                LexerException.class,
                () -> scan("SELECT @ FROM students")
        );

        assertTrue(exception.getMessage().contains("@"));
        assertTrue(exception.getMessage().contains("line 1"));
    }

    @Test
    void rejectsUnterminatedString() {
        LexerException exception = assertThrows(
                LexerException.class,
                () -> scan("INSERT INTO students VALUES ('Alice)")
        );

        assertTrue(
                exception.getMessage().contains(
                        "Unterminated string literal"
                )
        );
    }

    @Test
    void addsEofForEmptyInput() {
        List<Token> tokens = scan("");

        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type());
    }
}
