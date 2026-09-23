export type Properties = Readonly<Record<string, string>>;

const LINE_BREAK = /\r?\n/;
const COMMENT_MARKERS: ReadonlySet<string> = new Set(['#', '!']);
const KEY_TERMINATORS: ReadonlySet<string> = new Set(['=', ':', ' ', '\t', '\f']);
const SEPARATORS: ReadonlySet<string> = new Set(['=', ':']);
const WHITESPACE = /\s/;
const ESCAPE = '\\';
const ESCAPE_SEQUENCE_LENGTH = 2;
const ESCAPE_SEQUENCE = /\\(u[\da-fA-F]{4}|.)/g;
const UNICODE_MARKER = 'u';
const HEXADECIMAL_RADIX = 16;
const ESCAPED_CHARACTERS: Readonly<Record<string, string>> = Object.freeze({
  n: '\n',
  t: '\t',
  r: '\r',
  f: '\f',
});
const ENVIRONMENT_KEY_SEPARATORS = /[.-]/g;
const ENVIRONMENT_KEY_SEPARATOR = '_';

interface Cursor {
  readonly text: string;
  position: number;
}

function unescapeSequence(sequence: string): string {
  if (sequence.length > 1 && sequence.startsWith(UNICODE_MARKER)) {
    return String.fromCharCode(Number.parseInt(sequence.slice(1), HEXADECIMAL_RADIX));
  }
  return ESCAPED_CHARACTERS[sequence] ?? sequence;
}

function unescape(raw: string): string {
  return raw.replace(ESCAPE_SEQUENCE, (_match, sequence: string) => unescapeSequence(sequence));
}

function skipWhitespace(cursor: Cursor): void {
  while (WHITESPACE.test(cursor.text.charAt(cursor.position))) {
    cursor.position += 1;
  }
}

function readKey(cursor: Cursor): string {
  const start = cursor.position;
  while (cursor.position < cursor.text.length) {
    const character = cursor.text.charAt(cursor.position);
    if (KEY_TERMINATORS.has(character)) {
      break;
    }
    cursor.position += character === ESCAPE ? ESCAPE_SEQUENCE_LENGTH : 1;
  }
  return unescape(cursor.text.slice(start, cursor.position));
}

function skipSeparator(cursor: Cursor): void {
  skipWhitespace(cursor);
  if (SEPARATORS.has(cursor.text.charAt(cursor.position))) {
    cursor.position += 1;
    skipWhitespace(cursor);
  }
}

function isContent(line: string): boolean {
  return line.length > 0 && !COMMENT_MARKERS.has(line.charAt(0));
}

function continuesOnNextLine(line: string): boolean {
  let backslashes = 0;
  while (line.charAt(line.length - 1 - backslashes) === ESCAPE) {
    backslashes += 1;
  }
  return backslashes % ESCAPE_SEQUENCE_LENGTH === 1;
}

export function logicalLines(text: string): string[] {
  const lines: string[] = [];
  let pending: string | undefined;
  for (const physical of text.split(LINE_BREAK)) {
    const line = physical.trimStart();
    if (pending === undefined && !isContent(line)) {
      continue;
    }
    const joined = (pending ?? '') + line;
    pending = continuesOnNextLine(joined) ? joined.slice(0, -1) : undefined;
    if (pending === undefined) {
      lines.push(joined);
    }
  }
  return pending === undefined ? lines : [...lines, pending];
}

function parseLine(line: string): readonly [string, string] {
  const cursor: Cursor = { text: line, position: 0 };
  const key = readKey(cursor);
  skipSeparator(cursor);
  return [key, unescape(line.slice(cursor.position).trimEnd())];
}

export function parseProperties(text: string): Properties {
  return Object.fromEntries(logicalLines(text).map(parseLine));
}

export function toEnvironmentKey(key: string): string {
  return key.toUpperCase().replace(ENVIRONMENT_KEY_SEPARATORS, ENVIRONMENT_KEY_SEPARATOR);
}

export function toEnvironmentStyle(properties: Properties): Properties {
  return Object.fromEntries(
    Object.entries(properties).map(([key, value]) => [toEnvironmentKey(key), value]),
  );
}
