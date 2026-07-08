; Structure query for document outline.
; Captures:
;   @symbol — the definition node
;   @name   — the symbol name text

(function_definition
  name: (identifier) @name) @symbol

(class_definition
  name: (identifier) @name) @symbol
