; Highlight query — see https://tree-sitter.github.io/tree-sitter/syntax-highlighting
;
; Copy from your grammar's queries/highlights.scm or author one.

(keyword) @keyword
(string) @string
(comment) @comment
(number) @number
(identifier) @variable

(function_definition
  name: (identifier) @function)
