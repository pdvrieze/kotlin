// FIR_COMPARISON
fun returnFun() {}

fun usage() {
    re<caret>
    return
}

// ORDER: returnFun
// ORDER: return
