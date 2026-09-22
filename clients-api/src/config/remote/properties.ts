export type Properties = Readonly<Record<string, string>>;

const LINE_BREAK = /\r?\n/;
const COMMENT_MARKERS: ReadonlySet<string> = new Set(['#', '!']);
const KEY_TERMINATORS: ReadonlySet<string> = new Set(['=', ':', ' ', '\t', '\f']);
const SEPARATORS: ReadonlySet<string> = new Set(['=', ':']);
const WHITESPACE = /\s/;
const ESCAPE = '\\';
const ESCAPE_SEQUENCE_LENGTH = 2;
const ESCAPED_CHARACTERS: Readonly<Record<string, string>> = { n: '\n', t: '\t', r: '\r', f: '\f' };
const ENVIRONMENT_KEY_SEPARATORS = /[.-]/g;
const ENVIRONMENT_KEY_SEPARATOR = '_';
const SENSITIVE_KEY = /SECRET|PASSWORD|KEY|TOKEN/i;
export const REDACTED_VALUE = '[REDACTED]';

interface Cursor {
  readonly text: string;
  position: number;
}

function unescape(raw: string): string {
  return raw.replace(/\\(.)/g, (_match, character: string) => {
    return ESCAPED_CHARACTERS[character] ?? character;
  });
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

function parseLine(line: string): readonly [string, string] {
  const cursor: Cursor = { text: line, position: 0 };
  const key = readKey(cursor);
  skipSeparator(cursor);
  return [key, unescape(line.slice(cursor.position).trimEnd())];
}

export function parseProperties(text: string): Properties {
  const entries = text
    .split(LINE_BREAK)
    .map((line) => line.trimStart())
    .filter(isContent)
    .map(parseLine);
  return Object.fromEntries(entries);
}

export function toEnvironmentKey(key: string): string {
  return key.toUpperCase().replace(ENVIRONMENT_KEY_SEPARATORS, ENVIRONMENT_KEY_SEPARATOR);
}

export function toEnvironmentStyle(properties: Properties): Properties {
  return Object.fromEntries(
    Object.entries(properties).map(([key, value]) => [toEnvironmentKey(key), value]),
  );
}

export function isSensitiveKey(key: string): boolean {
  return SENSITIVE_KEY.test(key);
}

export function redactSensitive(properties: Properties): Properties {
  return Object.fromEntries(
    Object.entries(properties).map(([key, value]) => [
      key,
      isSensitiveKey(key) ? REDACTED_VALUE : value,
    ]),
  );
}
