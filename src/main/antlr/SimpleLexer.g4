lexer grammar SimpleLexer;

// Comments
COMMENTS : '//' ~[\r\n]+ -> skip ;

// Whitespace
NEWLINE            : ('\r\n' | 'r' | '\n') -> skip ;
WS                 : [\t ]+ -> skip ;

// Keywords
VAR                : 'var' ;
FUN                : 'fun' ;
WHILE              : 'while' ;
IF                 : 'if' ;
ELSE               : 'else' ;
RETURN             : 'return' ;

// Literals
LITERAL             : '0'|[1-9][0-9]* ;

// Operators
PLUS               : '+' ;
MINUS              : '-' ;
ASTERISK           : '*' ;
DIVISION           : '/' ;
MODULUS            : '%' ;
ASSIGN             : '=' ;

GEQ                : '>=' ;
LEQ                : '<=' ;
EQ                 : '==' ;
NEQ                : '!=' ;
GR                 : '>' ;
LS                 : '<' ;
OR                 : '||' ;
AND                : '&&' ;

COMMA              : ',' ;
LPAREN             : '(' ;
RPAREN             : ')' ;
LBRACE             : '{' ;
RBRACE             : '}' ;

// Identifiers
ID : [_a-zA-Z][A-Za-z0-9_]* ;