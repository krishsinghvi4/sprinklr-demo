export function parseBulkAllowlistLines(raw: string): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const part of raw.split(/[\n,\t]+/)) {
    const trimmed = part.trim()
    if (trimmed && !seen.has(trimmed)) {
      seen.add(trimmed)
      result.push(trimmed)
    }
  }
  return result
}

export function formatAllowlistLines(values: string[]): string {
  return values.join('\n')
}
