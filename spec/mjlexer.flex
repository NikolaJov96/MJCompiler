package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline + 1, yycolumn);
	}
	
	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline + 1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\n" 	{ }
"\r" 	{ }
"\r\n"	{ }
"\f" 	{ }

"program"   { return new_symbol(sym.PROG, yytext()); }
"break"     { return new_symbol(sym.BREAK, yytext()); }
"enum"      { return new_symbol(sym.ENUM, yytext()); }
"else"      { return new_symbol(sym.ELSE, yytext()); }
"const"     { return new_symbol(sym.CONST, yytext()); }
"if"        { return new_symbol(sym.IF, yytext()); }
"new"       { return new_symbol(sym.NEW, yytext()); }
"print" 	{ return new_symbol(sym.PRINT, yytext()); }
"read"      { return new_symbol(sym.READ, yytext()); }
"return" 	{ return new_symbol(sym.RETURN, yytext()); }
"void" 		{ return new_symbol(sym.VOID, yytext()); }
"for"       { return new_symbol(sym.FOR, yytext()); }
"continue"  { return new_symbol(sym.CONTINUE, yytext()); }

"true"|"false"			{ return new_symbol(sym.BOOLCONST, new Boolean(yytext())); }
[0-9]+  				{ return new_symbol(sym.NUMCONST, new Integer (yytext())); }
[a-zA-Z][a-zA-Z0-9_]*	{ return new_symbol (sym.IDENT, yytext()); }
"'"[ -\}]"'"  			{ return new_symbol(sym.CHARCONST, new Character(yytext().charAt(1))); }

"+" 		{ return new_symbol(sym.PLUS, yytext()); }
"-" 		{ return new_symbol(sym.MINUS, yytext()); }
"*" 		{ return new_symbol(sym.MUL, yytext()); }
"/" 		{ return new_symbol(sym.DIV, yytext()); }
"%" 		{ return new_symbol(sym.MOD, yytext()); }
"==" 		{ return new_symbol(sym.CEQ, yytext()); }
"!=" 		{ return new_symbol(sym.CNEQ, yytext()); }
">" 		{ return new_symbol(sym.CGT, yytext()); }
">=" 		{ return new_symbol(sym.CGE, yytext()); }
"<" 		{ return new_symbol(sym.CLT, yytext()); }
"<=" 		{ return new_symbol(sym.CLE, yytext()); }
"&&" 		{ return new_symbol(sym.AND, yytext()); }
"||" 		{ return new_symbol(sym.OR, yytext()); }
"=" 		{ return new_symbol(sym.EQUAL, yytext()); }
"++" 		{ return new_symbol(sym.INC, yytext()); }
"--" 		{ return new_symbol(sym.DEC, yytext()); }
";" 		{ return new_symbol(sym.SEMI, yytext()); }
"," 		{ return new_symbol(sym.COMMA, yytext()); }
"." 		{ return new_symbol(sym.DOT, yytext()); }
"(" 		{ return new_symbol(sym.LPAREN, yytext()); }
")" 		{ return new_symbol(sym.RPAREN, yytext()); }
"[" 		{ return new_symbol(sym.LBRACK, yytext()); }
"]" 		{ return new_symbol(sym.RBRACK, yytext()); }
"{" 		{ return new_symbol(sym.LBRACE, yytext()); }
"}"			{ return new_symbol(sym.RBRACE, yytext()); }

<YYINITIAL> "//" { yybegin(COMMENT); }
<COMMENT> .      { yybegin(COMMENT); }
<COMMENT> "\r\n" { yybegin(YYINITIAL); }
<COMMENT> "\n" { yybegin(YYINITIAL); }

. { System.err.println("Leksicka greska (" + yytext() + ") u liniji " + (yyline + 1)); }
