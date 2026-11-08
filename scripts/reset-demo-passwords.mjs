import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { DemoClient, DemoError, readJson, verifyTarget, writeJson } from './demo-client.mjs'
import { ADMIN_PASSWORD, STUDENT_PASSWORD, demoPassword } from './seed-demo.mjs'

const PRIMARY_EMAIL = 'admin.primary@example.test'
const SECONDARY_EMAIL = 'admin.secondary@example.test'

async function login(client, config, email, password) {
  const result = await client.raw('POST', '/api/v1/auth/login', { email, password })
  verifyTarget(result.accessToken, config)
  return { email, password, token: result.accessToken, user: result.user }
}

async function tryLogin(client, config, email, candidates) {
  for (const password of [...new Set(candidates.filter(Boolean))]) {
    try { return await login(client, config, email, password) } catch { /* try the next saved value */ }
  }
  return null
}

async function listUsers(client, token) {
  const values = []
  for (let page = 0; page < 100; page++) {
    const result = await client.raw('GET', `/api/v1/admin/users?size=100&page=${page}`, undefined, token)
    values.push(...result.content)
    if (page + 1 >= result.totalPages) return values
  }
  throw new DemoError('Unexpectedly large user list; password reset stopped.')
}

async function resetPassword(client, token, user, password) {
  await client.raw('POST', `/api/v1/admin/users/${user.id}/password-reset`, { newPassword: password, expectedVersion: user.rowVersion }, token)
}

async function main() {
  const args = process.argv.slice(2)
  const flag = name => args.includes(name)
  const option = name => { const index = args.indexOf(name); return index >= 0 ? args[index + 1] : undefined }
  const directory = resolve(option('--state-dir') ?? '.')
  const config = await readJson(join(directory, 'config.json'))
  const accounts = await readJson(join(directory, 'accounts.json'))
  const force = flag('--force')
  const client = new DemoClient(config, accounts, { operations: {} }, directory)
  const saved = new Map(accounts.accounts.map(account => [account.email, account]))

  let primary = await tryLogin(client, config, PRIMARY_EMAIL, [ADMIN_PASSWORD, saved.get(PRIMARY_EMAIL)?.password])
  let secondary = await tryLogin(client, config, SECONDARY_EMAIL, [ADMIN_PASSWORD, saved.get(SECONDARY_EMAIL)?.password])
  if (!primary && !secondary) throw new DemoError('No demo administrator can authenticate. Restore the private manifest before resetting passwords.')

  if (!primary || primary.password !== ADMIN_PASSWORD) {
    if (!secondary) throw new DemoError('The primary administrator needs a second administrator to reset its password; seed the demo accounts first.')
    const target = (await listUsers(client, secondary.token)).find(user => user.email === PRIMARY_EMAIL)
    if (!target) throw new DemoError('The primary administrator account is missing from the target.')
    await resetPassword(client, secondary.token, target, ADMIN_PASSWORD)
    primary = await tryLogin(client, config, PRIMARY_EMAIL, [ADMIN_PASSWORD])
    if (!primary) throw new DemoError('The primary administrator password reset could not be verified.')
  }

  if (!secondary || secondary.password !== ADMIN_PASSWORD) {
    const target = (await listUsers(client, primary.token)).find(user => user.email === SECONDARY_EMAIL)
    if (target) {
      await resetPassword(client, primary.token, target, ADMIN_PASSWORD)
      secondary = await tryLogin(client, config, SECONDARY_EMAIL, [ADMIN_PASSWORD])
    }
  }

  const users = await listUsers(client, primary.token)
  const untouched = new Set([primary.user.id, secondary?.user.id].filter(Boolean))
  let changed = 0, skipped = 0
  for (const user of users) {
    if (untouched.has(user.id)) continue
    const password = demoPassword(user.roles)
    const entry = saved.get(user.email)
    if (!force && entry?.password === password) { skipped++; continue }
    await resetPassword(client, primary.token, user, password)
    if (entry) entry.password = password
    changed++
  }

  for (const account of accounts.accounts) {
    const password = demoPassword(account.roles)
    if (account.password !== password) account.password = password
  }
  await writeJson(join(directory, 'accounts.json'), accounts)

  const checks = [PRIMARY_EMAIL, SECONDARY_EMAIL, 'demo.academic_admin@example.test', 'student.001@example.test', 'student.200@example.test']
  for (const email of checks) {
    const entry = saved.get(email)
    if (!entry) continue
    try { await login(client, config, email, demoPassword(entry.roles)) }
    catch { throw new DemoError(`Login verification failed for ${email}.`) }
  }

  console.log(`Demo passwords standardized: ${changed} reset, ${skipped} already current, ${users.length} accounts checked.`)
  if (!force) console.log('Use --force to rewrite every account password even when the manifest is already current.')
  console.log(`Administrators use ${ADMIN_PASSWORD}; students use ${STUDENT_PASSWORD}.`)
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1 })
}
