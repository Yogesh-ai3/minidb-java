
package minidb.sql;

public enum TokenType {
    // SQL keywords
    CREATE,
    DATABASE,
    USE,
    TABLE,
    INT,
    VARCHAR,
    PRIMARY,
    KEY,
    INSERT,
    INTO,
    VALUES,
    SELECT,
    FROM,
    WHERE,
    UPDATE,
    SET,
    DELETE,

    // General identifiers and literals
    IDENTIFIER,
    INTEGER_LITERAL,
    STRING_LITERAL,

    // Punctuation
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    DOT,
    STAR,
    SEMICOLON,

    // Operators
    EQUAL,
    GREATER_THAN,
    LESS_THAN,
    GREATER_EQUAL,
    LESS_EQUAL,
    NOT_EQUAL,

    // Marks the end of input
    EOF
}
