val foo = { <!UNRESOLVED_REFERENCE!>bar<!> }(

<!SYNTAX!><!SYNTAX!><!>val<!> bar <!SYNTAX!><!SYNTAX!>=<!> { <!TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM, TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM!>foo<!> }<!>
