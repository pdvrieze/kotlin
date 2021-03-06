package test

annotation class Ann(
        val b1: Boolean,
        val b2: Boolean,
        val b3: Boolean,
        val b4: Boolean,
        val b5: Boolean,
        val b6: Boolean
)

val a = 1
val b = 2

@Ann(<!ANNOTATION_ARGUMENT_MUST_BE_CONST!>1 > 2<!>, <!ANNOTATION_ARGUMENT_MUST_BE_CONST!>1.0 > 2.0<!>, <!ANNOTATION_ARGUMENT_MUST_BE_CONST!>2 > a<!>, <!ANNOTATION_ARGUMENT_MUST_BE_CONST!>b > a<!>, <!ANNOTATION_ARGUMENT_MUST_BE_CONST!>'b' > 'a'<!>, <!ANNOTATION_ARGUMENT_MUST_BE_CONST!>"a" > "b"<!>) class MyClass

// EXPECTED: @Ann(b1 = false, b2 = false, b3 = true, b4 = true, b5 = true, b6 = false)