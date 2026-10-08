import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHmac, randomBytes } from 'node:crypto'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, basename, dirname, resolve } from 'node:path'
import { DemoClient, verifyTarget } from './demo-client.mjs'

const config = { baseUrl: 'http://127.0.0.1:28080', issuer: 'campus-demo-test', audience: 'campus-demo-client', jwtSecret: randomBytes(32).toString('base64') }
const token = claims => {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256' })).toString('base64url')
  const body = Buffer.from(JSON.stringify({ iss: config.issuer, aud: [config.audience], exp: Math.floor(Date.now() / 1000) + 900, ...claims })).toString('base64url')
  return `${header}.${body}.${createHmac('sha256', Buffer.from(config.jwtSecret, 'base64')).update(header + '.' + body).digest('base64url')}`
}
test('target identity requires matching signature, issuer, audience and expiry', () => {
  assert.equal(verifyTarget(token({}), config).iss, config.issuer)
  for (const claims of [{ iss: 'other' }, { aud: ['other'] }, { exp: 1 }]) assert.throws(() => verifyTarget(token(claims), config))
  assert.throws(() => verifyTarget(token({}), { ...config, jwtSecret: randomBytes(32).toString('base64') }))
})
test('non-demo URLs and URL credentials are refused before HTTP', () => {
  for (const baseUrl of ['https://example.com', 'http://other:8080', 'http://user:password@localhost', 'http://localhost/other', 'http://localhost?x=1']) {
    assert.throws(() => new DemoClient({ ...config, baseUrl }, {}, {}, '', () => assert.fail()))
  }
})
async function fixture(t, handler) {
  const directory = await mkdtemp(join(tmpdir(), 'campus-demo-test-'))
  t.after(async () => {
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()))
    assert.ok(basename(directory).startsWith('campus-demo-test-'))
    await rm(directory, { recursive: true })
  })
  const state = { operations: {} }, calls = []
  const client = new DemoClient(config, {}, state, directory, async (url, options) => {
    calls.push([options.method, new URL(url).pathname])
    return handler(url, options)
  })
  client.sessions.set('ADMIN', { token: token({}), exp: Date.now() / 1000 + 900, user: { id: 'fixture-actor' } })
  return { client, state, calls }
}
const page = rows => Response.json({ content: rows, totalElements: rows.length, totalPages: 1 })
const resource = '/api/v1/admin/library/titles'
const body = { code: 'CD-TEST-001', title: 'Demo', author: 'Author' }
test('transport failure reconciles one committed resource without replaying POST', async t => {
  let created = false
  const row = { ...body, id: 'id-1', rowVersion: 0 }
  const { client, state, calls } = await fixture(t, (url, options) => {
    if (options.method === 'POST') { created = true; throw new Error('connection lost') }
    if (new URL(url).pathname.includes('/audits/')) return page([{ actorId: 'fixture-actor', targetId: row.id, action: 'CREATED' }])
    return page(created ? [row] : [])
  })
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body))
  assert.equal(state.operations['title:0'].pending, true)
  assert.equal((await client.create('title:0', 'ADMIN', resource, body)).id, row.id)
  assert.equal(calls.filter(([method]) => method === 'POST').length, 1)
})
test('a matching pending resource created by another actor is not adopted', async t => {
  let created = false
  const row = { ...body, id: 'foreign' }
  const { client, calls } = await fixture(t, (url, options) => {
    if (options.method === 'POST') { created = true; throw new Error('connection lost') }
    if (new URL(url).pathname.includes('/audits/')) return page([{ actorId: 'other-actor', targetId: row.id, action: 'CREATED' }])
    return page(created ? [row] : [])
  })
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body))
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body), /adoption refused/)
  assert.equal(calls.filter(([method]) => method === 'POST').length, 1)
})
test('uncertain absent write is refused rather than replayed', async t => {
  const { client, calls } = await fixture(t, (_url, options) => { if (options.method === 'POST') throw new Error('connection lost'); return page([]) })
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body))
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body), /Uncertain previous write/)
  assert.equal(calls.filter(([method]) => method === 'POST').length, 1)
})
test('existing unowned identifiers are never overwritten or adopted', async t => {
  const { client, calls } = await fixture(t, () => page([{ ...body, id: 'foreign' }]))
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body), /Unowned or duplicate/)
  assert.ok(calls.every(([method]) => method === 'GET'))
})
test('definitive rejection leaves no ambiguous operation and safe corrected retry works', async t => {
  let reject = true
  const { client, state } = await fixture(t, (_url, options) => options.method === 'GET' ? page([]) : reject ? new Response('', { status: 409 }) : Response.json({ ...body, id: 'id-1' }, { status: 201 }))
  await assert.rejects(client.create('title:0', 'ADMIN', resource, body), /HTTP 409/)
  assert.equal(state.operations['title:0'], undefined)
  reject = false
  assert.equal((await client.create('title:0', 'ADMIN', resource, body)).id, 'id-1')
})
test('completed creates validate saved ID and changed definitions without another write', async t => {
  const row = { ...body, id: 'id-1' }
  const { client, calls } = await fixture(t, (url, options) => options.method === 'POST' ? Response.json(row) : new URL(url).pathname.endsWith('id-1') ? Response.json(row) : page([]))
  await client.create('title:0', 'ADMIN', resource, body)
  await client.create('title:0', 'ADMIN', resource, body)
  await assert.rejects(client.create('title:0', 'ADMIN', resource, { ...body, title: 'Changed' }), /definition changed/)
  assert.equal(calls.filter(([method]) => method === 'POST').length, 1)
})
test('lost mutation response reconciles state and version without replaying PUT', async t => {
  let changed = false
  const { client, calls } = await fixture(t, (_url, options) => {
    if (options.method === 'PUT') { changed = true; throw new Error('connection lost') }
    return Response.json({ id: 'id-1', status: changed ? 'RETURNED' : 'OPEN', rowVersion: changed ? 1 : 0 })
  })
  await assert.rejects(client.change('return:0', 'ADMIN', resource + '/id-1', {}, { status: 'RETURNED' }))
  await client.change('return:0', 'ADMIN', resource + '/id-1', {}, { status: 'RETURNED' })
  assert.equal(calls.filter(([method]) => method === 'PUT').length, 1)
})
test('unsafe version is rejected before a mutation request', async t => {
  const { client, calls } = await fixture(t, () => Response.json({ id: 'id-1', rowVersion: Number.MAX_SAFE_INTEGER + 1 }))
  await assert.rejects(client.change('change:0', 'ADMIN', resource + '/id-1', {}, { status: 'INACTIVE' }), /Unsafe row version/)
  assert.equal(calls.filter(([method]) => method === 'PUT').length, 0)
})
