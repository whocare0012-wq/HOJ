<template>
  <div class="blockly-editor">
    <div ref="canvas" class="blockly-canvas" :style="{ height: `${height}px` }"></div>
    <div class="blockly-code-header">
      <strong>{{ $t('m.Blockly_Generated_Python') }}</strong>
      <el-button size="small" :disabled="!code" @click="copyCode">
        {{ $t('m.Blockly_Copy_Python') }}
      </el-button>
    </div>
    <pre class="blockly-code" aria-live="polite">{{ code }}</pre>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import * as Blockly from 'blockly/core'
import 'blockly/blocks'
import { pythonGenerator, Order } from 'blockly/python'
import * as en from 'blockly/msg/en'
import * as zhHans from 'blockly/msg/zh-hans'
import * as zhHant from 'blockly/msg/zh-hant'
import * as ja from 'blockly/msg/ja'
import * as ko from 'blockly/msg/ko'
import myMessage from '@/common/message'
import i18n from '@/i18n'

let ojBlocksRegistered = false

const TOKEN_SCANNER_DEFINITION = [
  'class _HojTokenScanner:',
  '    def __init__(self):',
  '        self.data = None',
  '        self.pos = 0',
  '    def skip(self):',
  '        if self.data is None:',
  "            self.data = __import__('sys').stdin.read()",
  '        while self.pos < len(self.data) and self.data[self.pos].isspace():',
  '            self.pos += 1',
  '    def has_next(self):',
  '        self.skip()',
  '        return self.pos < len(self.data)',
  '    def word(self):',
  '        self.skip()',
  '        if self.pos >= len(self.data):',
  "            raise EOFError('no more input')",
  '        start = self.pos',
  '        while self.pos < len(self.data) and not self.data[self.pos].isspace():',
  '            self.pos += 1',
  '        return self.data[start:self.pos]',
  '    def char(self):',
  '        self.skip()',
  '        if self.pos >= len(self.data):',
  "            raise EOFError('no more input')",
  '        result = self.data[self.pos]',
  '        self.pos += 1',
  '        return result',
  '',
  '_hoj_token_scanner = _HojTokenScanner()',
].join('\n')

function ensureTokenScanner(generator) {
  generator.definitions_.hoj_token_scanner = TOKEN_SCANNER_DEFINITION
  return '_hoj_token_scanner'
}

function ensureCppDivision(generator) {
  return generator.provideFunction_('hoj_cpp_div',
    `def ${generator.FUNCTION_NAME_PLACEHOLDER_}(a, b):\n  a = int(a)\n  b = int(b)\n  if b == 0:\n    raise ZeroDivisionError('integer division by zero')\n  quotient = abs(a) // abs(b)\n  return -quotient if (a < 0) != (b < 0) else quotient\n`)
}

function registerOjBlocks() {
  if (ojBlocksRegistered) return
  pythonGenerator.addReservedWords('_HojTokenScanner,_hoj_token_scanner')
  Blockly.defineBlocksWithJsonArray([
    { type: 'oj_read_line', message0: '%{BKY_OJ_READ_LINE}', output: 'String', colour: 180 },
    { type: 'oj_read_int', message0: '%{BKY_OJ_READ_INT}', output: 'Number', colour: 180 },
    { type: 'oj_read_int_list', message0: '%{BKY_OJ_READ_INT_LIST}', output: 'Array', colour: 180 },
    { type: 'oj_read_float', message0: '%{BKY_OJ_READ_FLOAT}', output: 'Number', colour: 180 },
    { type: 'oj_read_float_list', message0: '%{BKY_OJ_READ_FLOAT_LIST}', output: 'Array', colour: 180 },
    { type: 'oj_read_all_ints', message0: '%{BKY_OJ_READ_ALL_INTS}', output: 'Array', colour: 180 },
    { type: 'oj_next_word', message0: '%{BKY_OJ_NEXT_WORD}', output: 'String', colour: 180, tooltip: '%{BKY_OJ_TOKEN_TIP}' },
    { type: 'oj_next_int', message0: '%{BKY_OJ_NEXT_INT}', output: 'Number', colour: 180, tooltip: '%{BKY_OJ_TOKEN_TIP}' },
    { type: 'oj_next_float', message0: '%{BKY_OJ_NEXT_FLOAT}', output: 'Number', colour: 180, tooltip: '%{BKY_OJ_TOKEN_TIP}' },
    { type: 'oj_next_char', message0: '%{BKY_OJ_NEXT_CHAR}', output: 'String', colour: 180, tooltip: '%{BKY_OJ_TOKEN_TIP}' },
    { type: 'oj_has_next', message0: '%{BKY_OJ_HAS_NEXT}', output: 'Boolean', colour: 180, tooltip: '%{BKY_OJ_TOKEN_TIP}' },
    {
      type: 'oj_read_matrix', message0: '%{BKY_OJ_READ_MATRIX}',
      args0: [{ type: 'input_value', name: 'ROWS', check: 'Number' }],
      output: 'Array', colour: 180,
    },
    {
      type: 'oj_split_ints', message0: '%{BKY_OJ_SPLIT_INTS}',
      args0: [{ type: 'input_value', name: 'TEXT', check: 'String' }],
      output: 'Array', colour: 180,
    },
    {
      type: 'oj_print_list', message0: '%{BKY_OJ_PRINT_LIST}',
      args0: [{ type: 'input_value', name: 'LIST', check: 'Array' }],
      previousStatement: null, nextStatement: null, colour: 180,
    },
    {
      type: 'oj_print_pair', message0: '%{BKY_OJ_PRINT_PAIR}',
      args0: [
        { type: 'input_value', name: 'FIRST' },
        { type: 'input_value', name: 'SECOND' },
      ],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_triple', message0: '%{BKY_OJ_PRINT_TRIPLE}',
      args0: [
        { type: 'input_value', name: 'FIRST' },
        { type: 'input_value', name: 'SECOND' },
        { type: 'input_value', name: 'THIRD' },
      ],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_no_newline', message0: '%{BKY_OJ_PRINT_NO_NEWLINE}',
      args0: [{ type: 'input_value', name: 'VALUE' }],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_trailing_space', message0: '%{BKY_OJ_PRINT_TRAILING_SPACE}',
      args0: [{ type: 'input_value', name: 'VALUE' }],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_lines', message0: '%{BKY_OJ_PRINT_LINES}',
      args0: [{ type: 'input_value', name: 'LIST', check: 'Array' }],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_separator', message0: '%{BKY_OJ_PRINT_SEPARATOR}',
      args0: [
        { type: 'input_value', name: 'LIST', check: 'Array' },
        { type: 'input_value', name: 'SEPARATOR', check: 'String' },
      ],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_fixed', message0: '%{BKY_OJ_PRINT_FIXED}',
      args0: [
        { type: 'input_value', name: 'VALUE', check: 'Number' },
        { type: 'input_value', name: 'DIGITS', check: 'Number' },
      ],
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_print_blank', message0: '%{BKY_OJ_PRINT_BLANK}',
      previousStatement: null, nextStatement: null, colour: 200,
    },
    {
      type: 'oj_cpp_div', message0: '%{BKY_OJ_CPP_DIV}',
      args0: [
        { type: 'input_value', name: 'LEFT', check: 'Number' },
        { type: 'input_value', name: 'RIGHT', check: 'Number' },
      ],
      output: 'Number', colour: 230,
    },
    {
      type: 'oj_cpp_mod', message0: '%{BKY_OJ_CPP_MOD}',
      args0: [
        { type: 'input_value', name: 'LEFT', check: 'Number' },
        { type: 'input_value', name: 'RIGHT', check: 'Number' },
      ],
      output: 'Number', colour: 230,
    },
    {
      type: 'oj_bitwise_binary', message0: '%{BKY_OJ_BITWISE_BINARY}',
      args0: [
        { type: 'input_value', name: 'LEFT', check: 'Number' },
        { type: 'field_dropdown', name: 'OP', options: [['&', 'AND'], ['|', 'OR'], ['^', 'XOR'], ['<<', 'LSHIFT'], ['>>', 'RSHIFT']] },
        { type: 'input_value', name: 'RIGHT', check: 'Number' },
      ],
      output: 'Number', colour: 230,
    },
    {
      type: 'oj_bitwise_not', message0: '%{BKY_OJ_BITWISE_NOT}',
      args0: [{ type: 'input_value', name: 'VALUE', check: 'Number' }],
      output: 'Number', colour: 230,
    },
    {
      type: 'oj_to_int', message0: '%{BKY_OJ_TO_INT}',
      args0: [{ type: 'input_value', name: 'VALUE' }],
      output: 'Number', colour: 60,
    },
    {
      type: 'oj_to_float', message0: '%{BKY_OJ_TO_FLOAT}',
      args0: [{ type: 'input_value', name: 'VALUE' }],
      output: 'Number', colour: 60,
    },
    {
      type: 'oj_to_string', message0: '%{BKY_OJ_TO_STRING}',
      args0: [{ type: 'input_value', name: 'VALUE' }],
      output: 'String', colour: 60,
    },
    {
      type: 'oj_char_code', message0: '%{BKY_OJ_CHAR_CODE}',
      args0: [{ type: 'input_value', name: 'VALUE', check: 'String' }],
      output: 'Number', colour: 60,
    },
    {
      type: 'oj_code_char', message0: '%{BKY_OJ_CODE_CHAR}',
      args0: [{ type: 'input_value', name: 'VALUE', check: 'Number' }],
      output: 'String', colour: 60,
    },
  ])
  pythonGenerator.forBlock.oj_read_line = () => ['input()', Order.FUNCTION_CALL]
  pythonGenerator.forBlock.oj_read_int = () => ['int(input())', Order.FUNCTION_CALL]
  pythonGenerator.forBlock.oj_read_int_list = () => [
    'list(map(int, input().split()))', Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_read_float = () => ['float(input())', Order.FUNCTION_CALL]
  pythonGenerator.forBlock.oj_read_float_list = () => [
    'list(map(float, input().split()))', Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_read_all_ints = () => [
    "list(map(int, __import__('sys').stdin.buffer.read().split()))", Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_next_word = (block, generator) => [
    `${ensureTokenScanner(generator)}.word()`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_next_int = (block, generator) => [
    `int(${ensureTokenScanner(generator)}.word())`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_next_float = (block, generator) => [
    `float(${ensureTokenScanner(generator)}.word())`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_next_char = (block, generator) => [
    `${ensureTokenScanner(generator)}.char()`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_has_next = (block, generator) => [
    `${ensureTokenScanner(generator)}.has_next()`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_read_matrix = (block, generator) => {
    const rows = generator.valueToCode(block, 'ROWS', Order.NONE) || '0'
    return [`[list(map(int, input().split())) for _ in range(int(${rows}))]`, Order.ATOMIC]
  }
  pythonGenerator.forBlock.oj_split_ints = (block, generator) => {
    const value = generator.valueToCode(block, 'TEXT', Order.NONE) || "''"
    return [`list(map(int, (${value}).split()))`, Order.FUNCTION_CALL]
  }
  pythonGenerator.forBlock.oj_print_list = (block, generator) => {
    const value = generator.valueToCode(block, 'LIST', Order.NONE) || '[]'
    return `print(*(${value}))\n`
  }
  const valueOr = (block, generator, name, fallback = "''") =>
    generator.valueToCode(block, name, Order.NONE) || fallback
  pythonGenerator.forBlock.oj_print_pair = (block, generator) =>
    `print(${valueOr(block, generator, 'FIRST')}, ${valueOr(block, generator, 'SECOND')})\n`
  pythonGenerator.forBlock.oj_print_triple = (block, generator) =>
    `print(${valueOr(block, generator, 'FIRST')}, ${valueOr(block, generator, 'SECOND')}, ${valueOr(block, generator, 'THIRD')})\n`
  pythonGenerator.forBlock.oj_print_no_newline = (block, generator) =>
    `print(${valueOr(block, generator, 'VALUE')}, end='')\n`
  pythonGenerator.forBlock.oj_print_trailing_space = (block, generator) =>
    `print(${valueOr(block, generator, 'VALUE')}, end=' ')\n`
  pythonGenerator.forBlock.oj_print_lines = (block, generator) =>
    `print(*(${valueOr(block, generator, 'LIST', '[]')}), sep='\\n')\n`
  pythonGenerator.forBlock.oj_print_separator = (block, generator) =>
    `print(*(${valueOr(block, generator, 'LIST', '[]')}), sep=${valueOr(block, generator, 'SEPARATOR', "' '")})\n`
  pythonGenerator.forBlock.oj_print_fixed = (block, generator) =>
    `print(format(float(${valueOr(block, generator, 'VALUE', '0')}), '.{}f'.format(int(${valueOr(block, generator, 'DIGITS', '2')}))))\n`
  pythonGenerator.forBlock.oj_print_blank = () => 'print()\n'
  pythonGenerator.forBlock.oj_cpp_div = (block, generator) => {
    const left = valueOr(block, generator, 'LEFT', '0')
    const right = valueOr(block, generator, 'RIGHT', '1')
    return [`${ensureCppDivision(generator)}(${left}, ${right})`, Order.FUNCTION_CALL]
  }
  pythonGenerator.forBlock.oj_cpp_mod = (block, generator) => {
    const left = valueOr(block, generator, 'LEFT', '0')
    const right = valueOr(block, generator, 'RIGHT', '1')
    const division = ensureCppDivision(generator)
    const remainder = generator.provideFunction_('hoj_cpp_mod',
      `def ${generator.FUNCTION_NAME_PLACEHOLDER_}(a, b):\n  a = int(a)\n  b = int(b)\n  return a - ${division}(a, b) * b\n`)
    return [`${remainder}(${left}, ${right})`, Order.FUNCTION_CALL]
  }
  pythonGenerator.forBlock.oj_bitwise_binary = (block, generator) => {
    const left = valueOr(block, generator, 'LEFT', '0')
    const right = valueOr(block, generator, 'RIGHT', '0')
    const operations = { AND: '&', OR: '|', XOR: '^', LSHIFT: '<<', RSHIFT: '>>' }
    return [`(${left} ${operations[block.getFieldValue('OP')]} ${right})`, Order.ATOMIC]
  }
  pythonGenerator.forBlock.oj_bitwise_not = (block, generator) => [
    `(~${valueOr(block, generator, 'VALUE', '0')})`, Order.ATOMIC,
  ]
  pythonGenerator.forBlock.oj_to_int = (block, generator) => [
    `int(${valueOr(block, generator, 'VALUE', '0')})`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_to_float = (block, generator) => [
    `float(${valueOr(block, generator, 'VALUE', '0')})`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_to_string = (block, generator) => [
    `str(${valueOr(block, generator, 'VALUE')})`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_char_code = (block, generator) => [
    `ord(${valueOr(block, generator, 'VALUE', "'A'")})`, Order.FUNCTION_CALL,
  ]
  pythonGenerator.forBlock.oj_code_char = (block, generator) => [
    `chr(int(${valueOr(block, generator, 'VALUE', '65')}))`, Order.FUNCTION_CALL,
  ]
  ojBlocksRegistered = true
}

export default {
  name: 'BlocklyWorkspace',
  props: {
    state: { type: Object, default: null },
    height: { type: Number, default: 550 },
  },
  emits: ['update:state', 'generated'],
  data() {
    return { code: '', workspace: null, resizeObserver: null, restoring: false }
  },
  mounted() {
    const locales = {
      'zh-CN': zhHans, 'zh-TW': zhHant, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko,
    }
    Blockly.setLocale(locales[i18n.locale.value] || en)
    Blockly.Msg.OJ_READ_LINE = this.$t('m.Blockly_Read_Line')
    Blockly.Msg.OJ_READ_INT = this.$t('m.Blockly_Read_Int')
    Blockly.Msg.OJ_READ_INT_LIST = this.$t('m.Blockly_Read_Int_List')
    Blockly.Msg.OJ_READ_FLOAT = this.$t('m.Blockly_Read_Float')
    Blockly.Msg.OJ_READ_FLOAT_LIST = this.$t('m.Blockly_Read_Float_List')
    Blockly.Msg.OJ_READ_ALL_INTS = this.$t('m.Blockly_Read_All_Ints')
    Blockly.Msg.OJ_NEXT_WORD = this.$t('m.Blockly_Next_Word')
    Blockly.Msg.OJ_NEXT_INT = this.$t('m.Blockly_Next_Int')
    Blockly.Msg.OJ_NEXT_FLOAT = this.$t('m.Blockly_Next_Float')
    Blockly.Msg.OJ_NEXT_CHAR = this.$t('m.Blockly_Next_Char')
    Blockly.Msg.OJ_HAS_NEXT = this.$t('m.Blockly_Has_Next')
    Blockly.Msg.OJ_TOKEN_TIP = this.$t('m.Blockly_Token_Tip')
    Blockly.Msg.OJ_READ_MATRIX = this.$t('m.Blockly_Read_Matrix')
    Blockly.Msg.OJ_SPLIT_INTS = this.$t('m.Blockly_Split_Ints')
    Blockly.Msg.OJ_PRINT_LIST = this.$t('m.Blockly_Print_List')
    Blockly.Msg.OJ_PRINT_PAIR = this.$t('m.Blockly_Print_Pair')
    Blockly.Msg.OJ_PRINT_TRIPLE = this.$t('m.Blockly_Print_Triple')
    Blockly.Msg.OJ_PRINT_NO_NEWLINE = this.$t('m.Blockly_Print_No_Newline')
    Blockly.Msg.OJ_PRINT_TRAILING_SPACE = this.$t('m.Blockly_Print_Trailing_Space')
    Blockly.Msg.OJ_PRINT_LINES = this.$t('m.Blockly_Print_Lines')
    Blockly.Msg.OJ_PRINT_SEPARATOR = this.$t('m.Blockly_Print_Separator')
    Blockly.Msg.OJ_PRINT_FIXED = this.$t('m.Blockly_Print_Fixed')
    Blockly.Msg.OJ_PRINT_BLANK = this.$t('m.Blockly_Print_Blank')
    Blockly.Msg.OJ_CPP_DIV = this.$t('m.Blockly_Cpp_Div')
    Blockly.Msg.OJ_CPP_MOD = this.$t('m.Blockly_Cpp_Mod')
    Blockly.Msg.OJ_BITWISE_BINARY = this.$t('m.Blockly_Bitwise_Binary')
    Blockly.Msg.OJ_BITWISE_NOT = this.$t('m.Blockly_Bitwise_Not')
    Blockly.Msg.OJ_TO_INT = this.$t('m.Blockly_To_Int')
    Blockly.Msg.OJ_TO_FLOAT = this.$t('m.Blockly_To_Float')
    Blockly.Msg.OJ_TO_STRING = this.$t('m.Blockly_To_String')
    Blockly.Msg.OJ_CHAR_CODE = this.$t('m.Blockly_Char_Code')
    Blockly.Msg.OJ_CODE_CHAR = this.$t('m.Blockly_Code_Char')
    registerOjBlocks()
    this.workspace = markRaw(Blockly.inject(this.$refs.canvas, {
      toolbox: this.toolbox(),
      sounds: false,
      trashcan: true,
      scrollbars: true,
      move: { scrollbars: true, drag: true, wheel: true },
      zoom: { controls: true, wheel: true, startScale: 0.9, minScale: 0.4, maxScale: 2 },
    }))
    this.restoring = true
    let restoreFailed = false
    if (this.state) {
      try {
        Blockly.serialization.workspaces.load(this.state, this.workspace)
      } catch (error) {
        restoreFailed = true
        console.error('Blockly draft restoration failed', error)
        this.workspace.clear()
        myMessage.error(this.$t('m.Blockly_Load_Failed'))
      }
    }
    this.restoring = false
    this.workspace.addChangeListener(this.onWorkspaceChange)
    if (!restoreFailed) this.onWorkspaceChange()
    this.resizeObserver = new ResizeObserver(() => Blockly.svgResize(this.workspace))
    this.resizeObserver.observe(this.$refs.canvas)
  },
  beforeUnmount() {
    this.resizeObserver?.disconnect()
    this.workspace?.dispose()
  },
  watch: {
    state(newState) {
      if (newState !== null || !this.workspace || !this.workspace.getAllBlocks(false).length) return
      this.workspace.clear()
    },
  },
  methods: {
    toolbox() {
      const category = (name, colour, contents) => ({ kind: 'category', name, colour, contents })
      const block = (type) => ({ kind: 'block', type })
      return {
        kind: 'categoryToolbox',
        contents: [
          category(this.$t('m.Blockly_Input_Output'), '#319b89', [
            block('oj_read_int'), block('oj_read_int_list'), block('oj_read_line'),
            block('oj_read_float'), block('oj_read_float_list'),
          ]),
          category(this.$t('m.Blockly_Output'), '#167a9f', [
            block('text_print'), block('oj_print_pair'), block('oj_print_triple'),
            block('oj_print_no_newline'), block('oj_print_trailing_space'),
            block('oj_print_list'), block('oj_print_lines'),
            block('oj_print_separator'), block('oj_print_fixed'), block('oj_print_blank'),
          ]),
          category(this.$t('m.Blockly_Logic'), '#5b80a5', [
            block('controls_if'), block('controls_ifelse'), block('logic_compare'),
            block('logic_operation'), block('logic_negate'), block('logic_boolean'),
            block('logic_null'), block('logic_ternary'),
          ]),
          category(this.$t('m.Blockly_Loops'), '#5b9c62', [
            block('controls_repeat_ext'), block('controls_whileUntil'),
            block('controls_for'), block('controls_forEach'), block('controls_flow_statements'),
          ]),
          category(this.$t('m.Blockly_Math'), '#5c68a6', [
            block('math_number'), block('math_arithmetic'),
            block('math_single'), block('math_trig'), block('math_constant'),
            block('math_number_property'), block('math_change'), block('math_round'),
            block('math_on_list'), block('math_constrain'), block('math_random_int'),
            block('math_random_float'), block('math_atan2'),
          ]),
          category(this.$t('m.Blockly_Cpp_Operators'), '#4964a1', [
            block('oj_cpp_div'), block('oj_cpp_mod'),
            block('oj_bitwise_binary'), block('oj_bitwise_not'),
          ]),
          category(this.$t('m.Blockly_Text'), '#a15c81', [
            block('text'), block('text_join'), block('text_append'), block('text_length'),
            block('text_isEmpty'), block('text_indexOf'), block('text_charAt'),
            block('text_getSubstring'), block('text_changeCase'), block('text_trim'),
            block('text_count'), block('text_replace'), block('text_reverse'),
          ]),
          category(this.$t('m.Blockly_Lists'), '#6c61a4', [
            block('lists_create_empty'), block('lists_create_with'), block('lists_repeat'),
            block('lists_length'), block('lists_isEmpty'), block('lists_indexOf'),
            block('lists_getIndex'), block('lists_setIndex'), block('lists_getSublist'),
            block('lists_sort'), block('lists_reverse'), block('lists_split'),
          ]),
          category(this.$t('m.Blockly_Conversions'), '#aa853a', [
            block('oj_to_int'), block('oj_to_float'), block('oj_to_string'),
            block('oj_char_code'), block('oj_code_char'),
          ]),
          { kind: 'category', name: this.$t('m.Blockly_Variables'), colour: '#aa7445', custom: 'VARIABLE' },
          { kind: 'category', name: this.$t('m.Blockly_Functions'), colour: '#9963a1', custom: 'PROCEDURE' },
        ],
      }
    },
    onWorkspaceChange(event) {
      if (this.restoring || event?.isUiEvent || !this.workspace) return
      this.code = pythonGenerator.workspaceToCode(this.workspace)
      this.$emit('update:state', Blockly.serialization.workspaces.save(this.workspace))
      this.$emit('generated', this.code)
    },
    async copyCode() {
      try {
        await this.$copyText(this.code)
        myMessage.success(this.$t('m.Blockly_Copy_Success'))
      } catch (error) {
        myMessage.error(this.$t('m.Blockly_Copy_Failed'))
      }
    },
  },
}
</script>

<style scoped>
.blockly-canvas { width: 100%; min-height: 300px; }
.blockly-code-header { display: flex; align-items: center; justify-content: space-between; margin: 12px 0 6px; }
.blockly-code { min-height: 90px; max-height: 240px; margin: 0; padding: 12px; overflow: auto; white-space: pre; background: #f5f7fa; border: 1px solid #e4e7ed; font-size: 13px; }
</style>
