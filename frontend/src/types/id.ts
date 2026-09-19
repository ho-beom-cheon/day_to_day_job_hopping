/** External identifiers stay strings throughout transport, mapping and rendering. */
export type Id = string;

export function readId(value: unknown): Id {
  if (typeof value !== "string") throw new TypeError("ID must be a JSON string");
  return value;
}
