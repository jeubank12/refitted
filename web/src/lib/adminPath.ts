// Base path this admin UI is mounted at. Defaults to /admin for refitted's own
// standalone deploy; litus-animae overrides NEXT_PUBLIC_ADMIN_BASE_PATH at build
// time (NEXT_PUBLIC_* vars are inlined at compile time, same mechanism as the
// Firebase config vars) to mount this same source at /refitted-admin instead.
//
// NOT used for the middleware `matcher` in proxy.ts -- Next.js requires that to
// be a static, literal value it can analyze at build time; an imported or
// dynamically-computed matcher is silently ignored. Each app declares its own
// small, hardcoded matcher instead. This constant is for everything else:
// runtime path comparisons, redirects, and link hrefs, which have no such
// restriction.
export const ADMIN_BASE_PATH = process.env.NEXT_PUBLIC_ADMIN_BASE_PATH || '/admin'
