/** An unsigned JWT with the given claims: the right shape for the console, never accepted by the gateway. */
export function fakeJwt(claims: Record<string, unknown>): string {
  const payload = btoa(JSON.stringify(claims)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `eyJhbGciOiJIUzI1NiJ9.${payload}.c2lnbmF0dXJl`;
}

/** Seconds since the epoch, the unit of the `exp` claim; midday, so the year reads the same in every timezone. */
export const EXPIRES_2030 = 1_909_137_600; // 2030-07-01T12:00:00Z
export const EXPIRED_2020 = 1_593_604_800; // 2020-07-01T12:00:00Z
