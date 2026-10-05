// Maintainer acceptance check, separate from the Docker-only end-user setup.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { join, resolve } from 'node:path'
import { readJson, writeJson } from './demo-client.mjs'

const argument = (name, fallback) => {
  const index = process.argv.indexOf(name)
  return index < 0 ? fallback : process.argv[index + 1]
}
const directory = resolve(argument('--state-dir', '.demo/docker'))
const web = argument('--web-url', 'http://localhost:3300')
const client = resolve(argument('--client-dir', '../campus-client'))
const target = new URL(web)
if (!['localhost', '127.0.0.1'].includes(target.hostname) || target.protocol !== 'http:' || target.username || target.password) throw new Error('Only local demo browser targets are supported.')
const require = createRequire(join(client, 'package.json'))
const { chromium, expect } = require('@playwright/test')
let checkpoint = 'initialization'
const report = { web, steps: [], verifiedAt: null }

async function main() {
  const accounts = (await readJson(join(directory, 'accounts.json'))).accounts
  const browser = await chromium.launch({ headless: true })
  async function contextFor(email, viewport = { width: 1366, height: 900 }) {
    checkpoint = 'login through frontend proxy'
    const account = accounts.find(value => value.email === email)
    assert.ok(account)
    const context = await browser.newContext({ baseURL: web, viewport })
    const page = await context.newPage()
    page.setDefaultTimeout(15_000)
    await page.goto('/login')
    await page.getByLabel('Email', { exact: true }).fill(account.email)
    await page.getByLabel('Mật khẩu', { exact: true }).fill(account.password)
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page.getByRole('banner').getByText(email, { exact: true })).toBeVisible()
    return { context, page }
  }
  try {
    const { context, page } = await contextFor('admin.primary@example.test')
    checkpoint = 'private cookie and frontend data'
    const initial = (await context.cookies()).find(cookie => cookie.name === 'CAMPUS_REFRESH')
    assert.ok(initial?.httpOnly && !initial.secure && initial.sameSite === 'Lax' && initial.path === '/api/v1/auth')
    assert.equal(await page.evaluate(() => document.cookie.includes('CAMPUS_REFRESH')), false)
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0)
    await page.goto('/admin/students')
    await expect(page.getByText('CD20260001', { exact: true })).toBeVisible()
    await page.screenshot({ path: join(directory, 'review-admin-students.png'), fullPage: true })
    report.steps.push('ADMIN normal login, HttpOnly cookie, no browser token storage and seeded Student view PASS')
    checkpoint = 'reload, Origin protection and two tabs'
    const refresh = page.waitForResponse(response => response.url().endsWith('/auth/refresh') && response.status() === 200)
    await page.reload(); await refresh
    await expect(page.getByRole('banner').getByText('admin.primary@example.test', { exact: true })).toBeVisible()
    const rotated = (await context.cookies()).find(cookie => cookie.name === 'CAMPUS_REFRESH')
    assert.notEqual(rotated.value, initial.value)
    const forbidden = await context.request.post('/api/v1/auth/refresh', { headers: { Origin: 'https://untrusted.example.test' } })
    assert.equal(forbidden.status(), 403)
    const second = await context.newPage(); await second.goto('/')
    await expect(second.getByRole('banner').getByText('admin.primary@example.test', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
    await expect(second.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible()
    assert.equal((await context.cookies()).some(cookie => cookie.name === 'CAMPUS_REFRESH'), false)
    report.steps.push('Refresh rotation/reload, denied Origin and cross-tab logout PASS')
    await context.close()

    const finance = await contextFor('demo.finance_admin@example.test')
    checkpoint = 'scoped menu, direct route and HTTP permissions'
    await expect(finance.page.getByRole('link', { name: 'Tài khoản', exact: true })).toHaveCount(0)
    await expect(finance.page.getByRole('link', { name: 'Ký túc xá & tài chính', exact: true })).toBeVisible()
    const status = await finance.page.evaluate(async () => navigator.locks.request('campus-auth-cookie', async () => {
      const login = await (await fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })).json()
      const headers = { Authorization: 'Bearer ' + login.accessToken }
      const read = await fetch('/api/v1/admin/finance/fees', { headers })
      const denied = await fetch('/api/v1/admin/users', { headers })
      return { read: read.status, denied: denied.status }
    }))
    assert.deepEqual(status, { read: 200, denied: 403 })
    const accountRequests = []
    finance.page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/v1/admin/users')) accountRequests.push(request.url()) })
    await finance.page.goto('/admin/users')
    await expect(finance.page.getByText('Bạn không có quyền truy cập', { exact: true })).toBeVisible()
    assert.equal(accountRequests.length, 0)
    report.steps.push('FINANCE_ADMIN functional menu/API and forbidden account UI/API PASS')
    await finance.context.close()

    const student = await contextFor('student.001@example.test', { width: 390, height: 844 })
    checkpoint = 'mobile Student inbox and Event catalog'
    const administrativeRequests = []
    student.page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/v1/admin/')) administrativeRequests.push(request.url()) })
    await student.page.goto('/portal/inbox')
    await expect(student.page.getByRole('heading', { name: 'Hộp thư của bạn' })).toBeVisible()
    await expect(student.page.locator('.portal-inbox .ant-card')).toHaveCount(6)
    assert.equal(await student.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await student.page.screenshot({ path: join(directory, 'review-student-mobile.png'), fullPage: true })
    await student.page.goto('/portal/events')
    await expect(student.page.getByRole('heading', { name: 'Khám phá sự kiện' })).toBeVisible()
    await expect(student.page.locator('.portal-event-grid .ant-card').first()).toBeVisible()
    assert.equal(administrativeRequests.length, 0)
    report.steps.push('Linked Student mobile inbox/Event views, no ADMIN requests and no page overflow PASS')
    await student.context.close()
    report.verifiedAt = new Date().toISOString()
    await writeJson(join(directory, 'browser-review.json'), report)
    console.log(JSON.stringify(report, null, 2))
  }
  finally { await browser.close() }
}
main().catch(async () => {
  report.failedCheckpoint = checkpoint
  await writeJson(join(directory, 'browser-review.json'), report)
  console.error(`Demo browser acceptance stopped at: ${checkpoint}. Credentials, tokens and exception payloads are suppressed.`)
  process.exitCode = 1
})
