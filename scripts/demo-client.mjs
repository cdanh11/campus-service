import { createHmac, timingSafeEqual, createHash } from 'node:crypto'
import { readFile, writeFile, rename } from 'node:fs/promises'
import { join } from 'node:path'

export class DemoError extends Error {}
export async function readJson(path) { return JSON.parse((await readFile(path, 'utf8')).replace(/^\uFEFF/, '')) }
export async function writeJson(path, value) {
  await writeFile(path + '.tmp', JSON.stringify(value, null, 2), { mode: 0o600 })
  await rename(path + '.tmp', path)
}
export function verifyTarget(token, config) {
  const parts = token.split('.')
  if (parts.length !== 3) throw new DemoError('Target returned an invalid access token.')
  const header = JSON.parse(Buffer.from(parts[0], 'base64url'))
  const claims = JSON.parse(Buffer.from(parts[1], 'base64url'))
  const expected = createHmac('sha256', Buffer.from(config.jwtSecret, 'base64')).update(parts[0] + '.' + parts[1]).digest()
  const actual = Buffer.from(parts[2], 'base64url')
  if (header.alg !== 'HS256' || actual.length !== expected.length || !timingSafeEqual(actual, expected)
    || claims.iss !== config.issuer || ![claims.aud].flat().includes(config.audience)
    || !Number.isSafeInteger(claims.exp) || claims.exp <= Date.now() / 1000) {
    throw new DemoError('Target does not match this private demo configuration; no fixture writes are allowed.')
  }
  return claims
}
const fingerprint = value => createHash('sha256').update(JSON.stringify(value)).digest('hex')
const matchesField = (row, field, expected) => {
  if (['initialPassword', 'status', 'expectedVersion', 'expectedChargeVersion'].includes(field)) return true
  if (['startsAt', 'endsAt'].includes(field)) return Date.parse(row[field]) === Date.parse(expected)
  return JSON.stringify(row[field] ?? null) === JSON.stringify(expected ?? null)
}
export class DemoClient {
  constructor(config, accounts, state, directory, transport = fetch) {
    const target = new URL(config.baseUrl)
    if (target.protocol !== 'http:' || !['localhost', '127.0.0.1', 'backend'].includes(target.hostname)
      || target.username || target.password || target.pathname !== '/' || target.search || target.hash) {
      throw new DemoError('Only the explicitly configured loopback or demo Docker target is supported.')
    }
    this.config = config; this.accounts = accounts; this.state = state; this.directory = directory
    this.transport = transport; this.sessions = new Map(); this.cache = new Map(); this.writes = 0
  }
  async save() { await writeJson(join(this.directory, 'journal.json'), this.state) }
  async raw(method, path, body, token) {
    if (!path.startsWith('/api/v1/') || path.includes('..')) throw new DemoError('Unsupported API path.')
    const response = await this.transport(this.config.baseUrl + path, {
      method, redirect: 'error', signal: AbortSignal.timeout(30_000),
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
    if (!response.ok) throw new DemoError(`${method} ${path} returned HTTP ${response.status}; fixture loading stopped.`)
    const text = await response.text()
    return text ? JSON.parse(text) : null
  }
  async session(role) {
    const prior = this.sessions.get(role)
    if (prior && prior.exp > Date.now() / 1000 + 60) return prior
    const account = this.accounts.accounts.find(a => a.email === role || a.roles.includes(role))
    if (!account) throw new DemoError('A required private demo account is missing.')
    const login = await this.raw('POST', '/api/v1/auth/login', { email: account.email, password: account.password })
    const claims = verifyTarget(login.accessToken, this.config)
    if (JSON.stringify([...account.roles].sort()) !== JSON.stringify([...login.user.roles].sort())) throw new DemoError('Saved account permissions do not match the target.')
    const session = { token: login.accessToken, exp: claims.exp, user: login.user }
    this.sessions.set(role, session); return session
  }
  async request(role, method, path, body) {
    const { token } = await this.session(role)
    return this.raw(method, path, body, token)
  }
  async list(role, path, fresh = false) {
    if (!fresh && this.cache.has(path)) return this.cache.get(path)
    const values = []
    for (let page = 0; page < 1000; page++) {
      const result = await this.request(role, 'GET', `${path}${path.includes('?') ? '&' : '?'}size=100&page=${page}`)
      if (!Array.isArray(result.content) || !Number.isSafeInteger(result.totalElements)) throw new DemoError('Unexpected page response.')
      values.push(...result.content)
      if (page + 1 >= result.totalPages) { this.cache.set(path, values); return values }
    }
    throw new DemoError('Unexpectedly large data set; loading stopped.')
  }
  async confirmCreatedByOperator(role, path, id) {
    const source = path.includes('/users') ? 'IDENTITY'
      : /\/(organization-units|students|faculty-staff)(\/|$)/.test(path) ? 'PEOPLE'
        : path.includes('/academic/') ? 'ACADEMIC' : path.includes('/dormitory/') ? 'DORMITORY'
          : path.includes('/finance/') ? 'FINANCE' : path.includes('/notifications/') ? 'NOTIFICATION'
            : /\/(events|event-registrations)(\/|$)/.test(path) ? 'EVENT' : path.includes('/library/') ? 'LIBRARY' : null
    if (!source) throw new DemoError('Pending create has no approved owner audit source.')
    const actor = (await this.session(role)).user?.id
    if (!actor) throw new DemoError('Pending create has no authenticated operator identity.')
    const entries = await this.list('ADMIN', `/api/v1/admin/audits/${source}?targetId=${id}&actorId=${actor}`, true)
    if (!entries.some(entry => entry.targetId === id && entry.actorId === actor
      && ['CREATED', 'USER_CREATED', 'ENROLLED', 'ASSIGNED', 'RECORDED', 'REGISTERED', 'BORROWED'].includes(entry.action))) {
      throw new DemoError('Matching pending resource has no creation audit from the authenticated operator; adoption refused.')
    }
  }
  async create(key, role, path, body, { list = path, match = body } = {}) {
    if ('expectedChargeVersion' in body && (!Number.isSafeInteger(body.expectedChargeVersion) || body.expectedChargeVersion < 0)) throw new DemoError('Unsafe charge version; loading stopped.')
    const prior = this.state.operations[key]
    const definition = { ...body }; delete definition.expectedChargeVersion; delete definition.expectedVersion
    const digest = fingerprint({ role, path, body: definition })
    const detail = prior?.detail ?? `${list.split('?')[0]}/${prior?.id}`
    if (prior?.done) {
      if (prior.digest !== digest) throw new DemoError(`Fixture definition changed for ${key}; do not overwrite existing data.`)
      const value = await this.request(role, 'GET', detail)
      if (!value?.id || value.id !== prior.id) throw new DemoError(`Saved fixture ${key} is missing.`)
      return value
    }
    const rows = await this.list(role, list, !!prior)
    const matches = rows.filter(row => Object.entries(match).every(([field, expected]) => matchesField(row, field, expected)))
    if (prior) {
      if (prior.digest !== digest) throw new DemoError(`Pending fixture definition changed: ${key}.`)
      const added = matches.filter(row => !prior.before.includes(row.id))
      if (added.length === 1) {
        await this.confirmCreatedByOperator(role, path, added[0].id)
        if (path === '/api/v1/admin/users') {
          const login = await this.raw('POST', '/api/v1/auth/login', { email: body.email, password: body.initialPassword })
          verifyTarget(login.accessToken, this.config)
          if (login.user.id !== added[0].id) throw new DemoError('Pending account reconciliation failed.')
        }
        this.state.operations[key] = { done: true, digest, id: added[0].id, detail: `${list.split('?')[0]}/${added[0].id}` }
        await this.save(); return added[0]
      }
      throw new DemoError(`Uncertain previous write for ${key}; inspect the journal and API before resuming. No write was replayed.`)
    }
    if (matches.length) throw new DemoError(`Unowned or duplicate fixture identifier for ${key}; no existing record was changed.`)
    this.state.operations[key] = { digest, before: rows.map(row => row.id), pending: true }
    await this.save()
    let value
    try { value = await this.request(role, 'POST', path, body) }
    catch (failure) {
      // A definitive HTTP rejection did not create a resource. Transport failures
      // remain pending and require reconciliation rather than blind retry.
      if (failure instanceof DemoError && /returned HTTP (400|401|403|404|409|422);/.test(failure.message)) {
        delete this.state.operations[key]; await this.save()
      }
      throw failure
    }
    if (!value?.id) throw new DemoError(`Unexpected create response for ${key}; reconcile before resuming.`)
    this.writes++
    rows.push(value)
    this.state.operations[key] = { done: true, digest, id: value.id, detail: `${list.split('?')[0]}/${value.id}` }
    await this.save(); return value
  }
  async change(key, role, path, body, desired, { method = 'PUT', detail = path, unwrap = value => value } = {}) {
    if ('expectedChargeVersion' in body && (!Number.isSafeInteger(body.expectedChargeVersion) || body.expectedChargeVersion < 0)) throw new DemoError('Unsafe charge version; loading stopped.')
    const prior = this.state.operations[key]
    if (prior?.done) return unwrap(await this.request(role, 'GET', detail))
    const current = unwrap(await this.request(role, 'GET', detail))
    if (prior) {
      if (JSON.stringify(prior.desired) !== JSON.stringify(desired)) throw new DemoError(`Pending mutation definition changed for ${key}.`)
      if (Object.entries(desired).every(([k, v]) => current[k] === v) && current.rowVersion > prior.version) {
        this.state.operations[key] = { done: true, id: current.id, detail }
        await this.save(); return current
      }
      throw new DemoError(`Uncertain previous mutation for ${key}; no mutation was replayed.`)
    }
    if (!Number.isSafeInteger(current.rowVersion) || current.rowVersion < 0) throw new DemoError('Unsafe row version; loading stopped.')
    const request = { ...body, expectedVersion: current.rowVersion }
    this.state.operations[key] = { pending: true, version: current.rowVersion, desired }
    await this.save()
    let value
    try { value = await this.request(role, method, path, request) }
    catch (failure) {
      if (failure instanceof DemoError && /returned HTTP (400|401|403|404|409|422);/.test(failure.message)) {
        delete this.state.operations[key]; await this.save()
      }
      throw failure
    }
    this.writes++
    if (value?.id !== current.id || !Number.isSafeInteger(value.rowVersion)
      || Object.entries(desired).some(([field, expected]) => value[field] !== expected)) {
      throw new DemoError(`Unexpected mutation response for ${key}; reconcile before resuming.`)
    }
    this.state.operations[key] = { done: true, id: value.id, detail }
    await this.save(); return value
  }
}
