/** Iterates CSV records without splitting on quoted line breaks. */
export function* csvRecords(text: string): Generator<string[]> {
  let i = text.charCodeAt(0) === 0xfeff ? 1 : 0
  while (i < text.length) {
    const fields: string[] = []
    for (;;) {
      let field: string
      if (text[i] === '"') {
        const pieces: string[] = []
        let start = ++i
        for (;;) {
          const end = text.indexOf('"', i)
          if (end < 0) throw new Error('Unterminated quoted CSV field')
          pieces.push(text.slice(start, end))
          i = end + 1
          if (text[i] !== '"') break
          pieces.push('"')
          start = ++i
        }
        field = pieces.join('')
        while (text[i] === ' ' || text[i] === '\t') i++
        if (i < text.length && !',\r\n'.includes(text[i]))
          throw new Error('Unexpected text after a quoted CSV field')
      } else {
        const start = i
        while (i < text.length && !',\r\n'.includes(text[i])) {
          if (text[i] === '"') throw new Error('Unexpected quote in an unquoted CSV field')
          i++
        }
        field = text.slice(start, i)
      }
      if (field.length > 1_000_000) throw new Error('CSV field exceeds length limit')
      fields.push(field)
      if (text[i] !== ',') break
      i++
    }
    if (text[i] === '\r') i++
    if (text[i] === '\n') i++
    if (fields.length > 1 || fields[0].trim()) yield fields
  }
}
export function writeCsv(columns: readonly string[], rows: readonly object[]): string {
  const lines = [columns.join(',')]
  for (const row of rows) {
    lines.push(
      columns
        .map((key) => {
          const value = String((row as Record<string, unknown>)[key] ?? '')
          return /[,"\r\n]/.test(value) ? '"' + value.replace(/"/g, '""') + '"' : value
        })
        .join(','),
    )
  }
  return lines.join('\n') + '\n'
}
