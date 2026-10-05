import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { randomBytes } from 'node:crypto'
import { join, resolve } from 'node:path'
import { DemoClient, DemoError, readJson, writeJson } from './demo-client.mjs'

async function main() {
  const position = process.argv.indexOf('--state-dir')
  const root = resolve(position < 0 ? '.demo/docker' : process.argv[position + 1])
  const config = await readJson(join(root, 'config.json'))
  if (config.baseUrl === 'http://backend:8080') config.baseUrl = 'http://127.0.0.1:28080'
  const accounts = await readJson(join(root, 'accounts.json'))
  const directory = join(root, 'resume-check')
  await mkdir(directory, { recursive: true, mode: 0o700 })
  let state
  try { state = await readJson(join(directory, 'journal.json')) }
  catch (error) { if (error.code !== 'ENOENT') throw error; state = { operations: {} } }
  const path = '/api/v1/admin/library/titles'
  const body = { code: 'CD-VERIFY-RESUME', title: 'Mẫu kiểm tra khôi phục demo', author: 'Campus Platform Demo' }
  let posts = 0
  const transport = async (url, options) => {
    const response = await fetch(url, options)
    if (options.method === 'POST' && new URL(url).pathname === path && response.ok) {
      posts++; await response.arrayBuffer()
      throw new Error('Controlled lost acknowledgement after commit')
    }
    return response
  }
  const client = new DemoClient(config, accounts, state, directory, transport)
  const actor = (await client.session('LIBRARY_ADMIN')).user.id
  if (state.actor && state.actor !== actor) throw new DemoError('Resume verification journal belongs to a different target.')
  state.actor = actor
  if (process.argv.includes('--reconcile-existing') && state.operations['verify:resume']?.done) {
    const recorded = state.operations['verify:resume']
    state.operations['verify:resume'] = { pending: true, digest: recorded.digest, before: [] }
    await client.save()
  }
  const first = !state.operations['verify:resume']
  if (first) await assert.rejects(client.create('verify:resume', 'LIBRARY_ADMIN', path, body), /Controlled lost acknowledgement/)
  const recovered = new DemoClient(config, accounts, state, directory)
  const saved = await recovered.create('verify:resume', 'LIBRARY_ADMIN', path, body)
  assert.equal(saved.code, body.code)
  assert.equal((await recovered.list('LIBRARY_ADMIN', path, true)).filter(row => row.code === body.code).length, 1)
  assert.equal(recovered.writes, 0)
  assert.equal(posts, first ? 1 : 0)
  let puts = 0
  const detail = path + '/' + saved.id
  const mutator = new DemoClient(config, accounts, state, directory, async (url, options) => {
    const response = await fetch(url, options)
    if (options.method === 'PUT' && new URL(url).pathname === detail && response.ok) {
      puts++; await response.arrayBuffer(); throw new Error('Controlled lost mutation acknowledgement')
    }
    return response
  })
  const firstMutation = !state.operations['verify:inactive']
  if (firstMutation) await assert.rejects(mutator.change('verify:inactive', 'LIBRARY_ADMIN', detail, { ...body, status: 'INACTIVE' }, { status: 'INACTIVE' }), /Controlled lost mutation acknowledgement/)
  const mutationRecovered = await recovered.change('verify:inactive', 'LIBRARY_ADMIN', detail, { ...body, status: 'INACTIVE' }, { status: 'INACTIVE' })
  if (firstMutation) assert.equal(mutationRecovered.status, 'INACTIVE')
  assert.equal(puts, firstMutation ? 1 : 0)
  const restore = new DemoClient(config, accounts, state, directory)
  assert.equal((await restore.change('verify:active', 'LIBRARY_ADMIN', detail, { ...body, status: 'ACTIVE' }, { status: 'ACTIVE' })).status, 'ACTIVE')
  let wrongTargetWrites = 0
  const wrongTarget = new DemoClient({ ...config, jwtSecret: randomBytes(32).toString('base64') }, accounts, { operations: {} }, directory, async (url, options) => {
    if (options.method !== 'GET' && new URL(url).pathname !== '/api/v1/auth/login') wrongTargetWrites++
    return fetch(url, options)
  })
  await assert.rejects(wrongTarget.create('wrong:target', 'LIBRARY_ADMIN', path, body), /Target does not match/)
  assert.equal(wrongTargetWrites, 0)
  const result = { committedBeforeLostAcknowledgement: first ? 1 : 0, duplicatePostsDuringRecovery: recovered.writes,
    persistedMatchingTitles: 1, reconciledWithOwnerAudit: true, lostMutationReconciledWithoutReplay: true,
    wrongTargetOwnerWrites: wrongTargetWrites, verifiedAt: new Date().toISOString() }
  await writeJson(join(root, 'resume-review.json'), result)
  console.log(JSON.stringify(result, null, 2))
}
main().catch(error => { console.error(error instanceof DemoError ? error.message : 'Resume verification stopped; sensitive exception details are suppressed.'); process.exitCode = 1 })
