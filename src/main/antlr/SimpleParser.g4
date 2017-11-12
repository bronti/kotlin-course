parser grammar SimpleParser;

options { tokenVocab=SimpleLexer; }

file                : block EOF ;

block               : statement* ;

blockWithBraces     : LBRACE block RBRACE ;

statement           : function | variableDeclaration | expression | whileStatement | ifStatement | assignment | returnStatement ;

function            : FUN ID LPAREN parameterNames RPAREN blockWithBraces ;

variableDeclaration            : VAR ID (ASSIGN expression)? ;

parameterNames      : (ID (COMMA ID)*)? ;

whileStatement      : WHILE LPAREN expression RPAREN blockWithBraces ;

ifStatement         : IF LPAREN expression RPAREN blockWithBraces (ELSE blockWithBraces)? ;

assignment          : ID ASSIGN expression ;

returnStatement     : RETURN expression ;

//expression          : functionCall | binaryExpression | ID | LITERAL | LPAREN expression RPAREN ;

functionCall        : ID LPAREN arguments RPAREN ;

arguments           : (expression (COMMA expression)*)? ;

expression
    : functionCall                                                  # functionCallExpression
    | expression op=(ASTERISK | DIVISION | MODULUS)     expression  # multiplicationExpression
    | expression op=(PLUS | MINUS)                      expression  # summExpression
    | expression op=(GEQ | LEQ | EQ | NEQ | GR | LS)    expression  # compareExpression
    | expression op=(AND | OR)                          expression  # logicExpression
    | LITERAL                                                       # literalExpression
    | ID                                                            # variableExpression
    | LPAREN expression RPAREN                                      # parenthesesExpression
;

