import { randomBytes, createHash } from 'node:crypto'
import { open, unlink } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { DemoClient, DemoError, readJson, writeJson } from './demo-client.mjs'

const fields = (value, keys) => Object.fromEntries(keys.map(key => [key, value[key]]))
const base = '/api/v1/admin/'
const code = (kind, i) => `CD-${kind}-${String(i + 1).padStart(3, '0')}`
const names = ['Nguyễn Minh An', 'Trần Ngọc Anh', 'Lê Hoàng Bảo', 'Phạm Gia Bình', 'Hoàng Thanh Chi', 'Vũ Hải Dương', 'Đặng Quỳnh Giang', 'Bùi Khánh Hà', 'Đỗ Trung Hiếu', 'Hồ Mai Hương', 'Ngô Tuấn Khang', 'Dương Thảo Linh', 'Lý Đức Minh', 'Phan Bảo Ngọc', 'Võ Hải Phong', 'Trịnh Minh Quân', 'Đinh Thu Trang', 'Mai Bảo Trâm', 'Tạ Nhật Vy', 'Cao Anh Vũ']
const units = ['Công nghệ thông tin', 'Kinh tế', 'Ngoại ngữ', 'Kỹ thuật điện', 'Cơ khí', 'Xây dựng', 'Môi trường', 'Luật', 'Truyền thông', 'Du lịch', 'Khoa học cơ bản', 'Quản trị kinh doanh', 'Đào tạo', 'Công tác sinh viên', 'Tài chính', 'Hành chính nhân sự']
const subjects = ['Nhập môn ngành', 'Phương pháp nghiên cứu', 'Thực hành chuyên ngành', 'Ứng dụng dữ liệu', 'Đồ án cơ sở']
const events = ['Ngày hội việc làm', 'Tuần lễ định hướng', 'Cuộc thi lập trình', 'Hội thảo khởi nghiệp', 'Hiến máu tình nguyện', 'Ngày hội văn hóa', 'Giải chạy sinh viên', 'Tọa đàm doanh nghiệp', 'Triển lãm đồ án', 'Kỹ năng phỏng vấn', 'Ngày hội đọc sách', 'Bảo vệ môi trường', 'Sinh viên nghiên cứu khoa học', 'Giao lưu câu lạc bộ', 'Hội thảo học bổng', 'Kết nối cựu sinh viên']
const scoped = ['ORGANIZATION_ADMIN', 'STUDENT_ADMIN', 'PERSONNEL_ADMIN', 'ACADEMIC_ADMIN', 'DORMITORY_ADMIN', 'FINANCE_ADMIN', 'NOTIFICATION_ADMIN', 'EVENT_ADMIN', 'LIBRARY_ADMIN', 'AUDIT_VIEWER', 'REPORTING_VIEWER']

async function account(client, email, displayName, roles) {
  let value = client.accounts.accounts.find(a => a.email === email)
  if (!value) {
    value = { email, displayName, roles, password: randomBytes(32).toString('base64') }
    client.accounts.accounts.push(value)
    await writeJson(join(client.directory, 'accounts.json'), client.accounts)
  }
  const primary = await client.session('ADMIN')
  if (primary.user.email === email) return primary.user
  const known = await client.list('ADMIN', base + 'users')
  // Previously provisioned native demo accounts are adopted only after successful
  // authentication with their saved private password and exact role verification.
  if (!client.state.operations[`account:${email}`] && known.some(a => a.email === email)) {
    const session = await client.session(email)
    client.state.operations[`account:${email}`] = { adopted: true, id: session.user.id }
    await client.save(); return session.user
  }
  if (client.state.operations[`account:${email}`]?.adopted) return (await client.session(email)).user
  return client.create(`account:${email}`, 'ADMIN', base + 'users', {
    email, displayName: value.displayName ?? displayName, initialPassword: value.password, roles: value.roles, status: 'ACTIVE',
  })
}

export async function seedInventory(c) {
  const primary = await c.session('ADMIN')
  if (!c.state.anchor) { c.state.anchor = primary.user.id; await c.save() }
  if (c.state.anchor !== primary.user.id) throw new DemoError('Private manifest belongs to a different installation.')
  await account(c, 'admin.secondary@example.test', 'Quản trị hệ thống 2', ['ADMIN'])
  for (const role of scoped) await account(c, `demo.${role.toLowerCase()}@example.test`, `Demo ${role}`, [role])
  console.log('Verified global and functional demo accounts; credential values are private.')

  const org = [], students = [], users = [], personnel = [], programs = [], courses = [], terms = [], offerings = [], sections = []
  for (let i = 0; i < 16; i++) org.push(await c.create(`unit:${i}`, 'ORGANIZATION_ADMIN', base + 'organization-units', {
    code: code('UNIT', i), name: `${i < 12 ? 'Khoa' : 'Phòng'} ${units[i]}`, unitType: i < 12 ? 'FACULTY' : 'ADMINISTRATIVE', status: 'ACTIVE',
  }))
  for (let i = 0; i < 200; i++) {
    const email = `student.${String(i + 1).padStart(3, '0')}@example.test`
    const name = `${names[i % names.length]} ${String(Math.floor(i / names.length) + 1).padStart(2, '0')}`
    const user = await account(c, email, name, ['USER']); users.push(user)
    students.push(await c.create(`student:${i}`, 'STUDENT_ADMIN', base + 'students', {
      studentNumber: `CD2026${String(i + 1).padStart(4, '0')}`, fullName: name, email,
      identityUserId: user.id, organizationUnitId: org[i % 12].id, status: 'ACTIVE',
    }))
    if ((i + 1) % 50 === 0) console.log(`Student profiles verified: ${i + 1}/200`)
  }
  for (let i = 0; i < 80; i++) personnel.push(await c.create(`personnel:${i}`, 'PERSONNEL_ADMIN', base + 'faculty-staff', {
    personnelNumber: code('EMP', i), fullName: `${names[(i + 7) % names.length]} GV${Math.floor(i / names.length) + 1}`,
    email: `employee.${i + 1}@example.test`, identityUserId: null, personnelType: i < 60 ? 'FACULTY' : 'STAFF',
    organizationUnitId: org[i < 60 ? i % 12 : 12 + i % 4].id, status: 'ACTIVE',
  }))
  for (let i = 0; i < 12; i++) programs.push(await c.create(`program:${i}`, 'ACADEMIC_ADMIN', base + 'academic/programs', {
    code: code('PROG', i), name: `Cử nhân ${units[i]}`, organizationUnitId: org[i].id, status: 'ACTIVE',
  }))
  for (let i = 0; i < 60; i++) courses.push(await c.create(`course:${i}`, 'ACADEMIC_ADMIN', base + 'academic/courses', {
    code: code('COURSE', i), title: `${subjects[i % 5]} ${units[Math.floor(i / 5)]}`, credits: i % 5 === 2 ? 2 : 3,
    organizationUnitId: org[Math.floor(i / 5)].id, status: 'ACTIVE',
  }))
  for (let i = 0; i < 3; i++) {
    let term = await c.create(`term:${i}`, 'ACADEMIC_ADMIN', base + 'academic/terms', {
      code: code('TERM', i), name: `Học kỳ demo ${i + 1}`, startDate: `${2026 + i}-01-01`, endDate: `${2026 + i}-06-30`,
    })
    term = await c.change(`term:active:${i}`, 'ACADEMIC_ADMIN', base + `academic/terms/${term.id}`, { ...fields(term, ['code', 'name', 'startDate', 'endDate']), status: 'ACTIVE' }, { status: 'ACTIVE' }); terms.push(term)
  }
  for (let i = 0; i < 90; i++) {
    let offering = await c.create(`offering:${i}`, 'ACADEMIC_ADMIN', base + 'academic/offerings', { termId: terms[Math.floor(i / 30)].id, courseId: courses[i % 60].id })
    offering = await c.change(`offering:open:${i}`, 'ACADEMIC_ADMIN', base + `academic/offerings/${offering.id}`, { status: 'OPEN' }, { status: 'OPEN' }); offerings.push(offering)
  }
  for (let i = 0; i < 150; i++) {
    let section = await c.create(`section:${i}`, 'ACADEMIC_ADMIN', base + 'academic/sections', {
      offeringId: offerings[i % 90].id, code: code('CLASS', i), capacity: 40, facultyId: i < 140 ? personnel[i % 60].id : null,
    })
    if (i < 130) section = await c.change(`section:open:${i}`, 'ACADEMIC_ADMIN', base + `academic/sections/${section.id}`, { ...fields(section, ['code', 'capacity', 'facultyId']), status: 'OPEN' }, { status: 'OPEN' })
    if (i >= 140) section = await c.change(`section:cancel:${i}`, 'ACADEMIC_ADMIN', base + `academic/sections/${section.id}`, { ...fields(section, ['code', 'capacity', 'facultyId']), status: 'CANCELLED' }, { status: 'CANCELLED' })
    sections.push(section)
  }
  for (let i = 0; i < 800; i++) {
    const student = students[Math.floor(i / 4)], section = sections[(Math.floor(i / 4) * 7 + (i % 4) * 31) % 120]
    const enrollment = await c.create(`enrollment:${i}`, 'ACADEMIC_ADMIN', base + 'academic/enrollments', { studentId: student.id, sectionId: section.id })
    if (i % 10 === 0) await c.change(`enrollment:withdraw:${i}`, 'ACADEMIC_ADMIN', base + `academic/enrollments/${enrollment.id}`, { status: 'WITHDRAWN' }, { status: 'WITHDRAWN' })
    if (i % 40 === 0) await c.change(`enrollment:restore:${i}`, 'ACADEMIC_ADMIN', base + `academic/enrollments/${enrollment.id}`, { status: 'ENROLLED' }, { status: 'ENROLLED' })
  }
  for (let i = 120; i < 130; i++) await c.change(`section:close:${i}`, 'ACADEMIC_ADMIN', base + `academic/sections/${sections[i].id}`, { ...fields(sections[i], ['code', 'capacity', 'facultyId']), status: 'CLOSED' }, { status: 'CLOSED' })
  console.log('Academic catalog, delivery and 800 retained enrollments verified.')

  const buildings = [], rooms = [], beds = []
  for (let i = 0; i < 4; i++) buildings.push(await c.create(`building:${i}`, 'DORMITORY_ADMIN', base + 'dormitory/buildings', { code: code('BLD', i), name: `Ký túc xá ${String.fromCharCode(65 + i)}` }))
  for (let i = 0; i < 80; i++) rooms.push(await c.create(`room:${i}`, 'DORMITORY_ADMIN', base + 'dormitory/rooms', {
    code: code('ROOM', i), name: `Phòng ${Math.floor(i / 20) + 1}-${String(i % 20 + 1).padStart(2, '0')}`, parentId: buildings[Math.floor(i / 20)].id,
  }))
  for (let i = 0; i < 240; i++) beds.push(await c.create(`bed:${i}`, 'DORMITORY_ADMIN', base + 'dormitory/beds', {
    code: code('BED', i), name: `Giường ${i % 3 + 1}`, parentId: rooms[Math.floor(i / 3)].id,
  }))
  for (let i = 0; i < 20; i++) {
    const assignment = await c.create(`stay:history:${i}`, 'DORMITORY_ADMIN', base + 'dormitory/assignments', { studentId: students[100 + i].id, bedId: beds[100 + i].id })
    await c.change(`stay:release:${i}`, 'DORMITORY_ADMIN', base + `dormitory/assignments/${assignment.id}`, { status: 'RELEASED' }, { status: 'RELEASED' })
  }
  for (let i = 0; i < 100; i++) await c.create(`stay:current:${i}`, 'DORMITORY_ADMIN', base + 'dormitory/assignments', { studentId: students[i].id, bedId: beds[i].id })
  console.log('Dormitory inventory and 100 current/20 released assignments verified.')

  const fees = [], charges = [], payments = []
  const feeNames = ['Học phí học kỳ', 'Phí ký túc xá', 'Bảo hiểm sinh viên', 'Phí hoạt động', 'Phí tài liệu', 'Phí thực hành']
  const amounts = [6000000, 1500000, 750000, 200000, 300000, 500000]
  for (let i = 0; i < 6; i++) fees.push(await c.create(`fee:${i}`, 'FINANCE_ADMIN', base + 'finance/fees', { code: code('FEE', i), name: feeNames[i], amount: amounts[i] }))
  for (let i = 0; i < 600; i++) charges.push(await c.create(`charge:${i}`, 'FINANCE_ADMIN', base + 'finance/charges', {
    chargeNumber: code('CHARGE', i), studentId: students[Math.floor(i / 3)].id, feeId: fees[i % 6].id,
    dueDate: i % 3 === 0 ? '2026-09-01' : '2027-01-31',
  }))
  for (let i = 0; i < 450; i++) {
    const chargeIndex = i < 400 ? i : (i - 400) * 2
    const balance = await c.request('FINANCE_ADMIN', 'GET', base + `finance/charges/${charges[chargeIndex].id}/balance`)
    const payment = await c.create(`payment:${i}`, 'FINANCE_ADMIN', base + 'finance/payments', {
      receiptNumber: code('RECEIPT', i), chargeId: charges[chargeIndex].id,
      amount: amounts[chargeIndex % 6] / (i >= 400 || chargeIndex % 2 === 0 ? 2 : 1), expectedChargeVersion: balance.rowVersion,
    }); payments.push(payment)
  }
  for (let i = 0; i < 30; i++) {
    const balance = await c.request('FINANCE_ADMIN', 'GET', base + `finance/charges/${payments[i].chargeId}/balance`)
    await c.change(`payment:reverse:${i}`, 'FINANCE_ADMIN', base + `finance/payments/${payments[i].id}`, {
      status: 'REVERSED', reason: 'Demo: điều chỉnh biên nhận nhập nhầm, giữ lịch sử', expectedChargeVersion: balance.rowVersion,
    }, { status: 'REVERSED' })
  }
  for (let i = 550; i < 570; i++) await c.change(`charge:cancel:${i}`, 'FINANCE_ADMIN', base + `finance/charges/${charges[i].id}`, { status: 'CANCELLED' }, { status: 'CANCELLED' })
  console.log('Finance: 600 snapshots, 450 receipts and retained reversal/cancellation examples verified.')

  for (let i = 0; i < 24; i++) {
    const template = await c.create(`template:${i}`, 'NOTIFICATION_ADMIN', base + 'notifications/templates', {
      code: code('NOTICE', i), name: `Thông báo demo ${i + 1}`, title: `${['Lịch học', 'Học phí', 'Hoạt động sinh viên', 'Thư viện'][i % 4]} — đợt ${i + 1}`,
      body: `Dữ liệu minh họa đồ án Campus Platform. Thông báo đợt ${i + 1}: vui lòng xem thông tin trên ứng dụng và liên hệ đơn vị phụ trách khi cần hỗ trợ.`,
    })
    const notice = await c.create(`notice:${i}`, 'NOTIFICATION_ADMIN', base + 'notifications/notices', { templateId: template.id })
    const recipientIds = Array.from({ length: 50 }, (_, j) => users[(i * 25 + j) % 200].id)
    await c.change(`notice:publish:${i}`, 'NOTIFICATION_ADMIN', base + `notifications/notices/${notice.id}/publish`, { recipientIds }, { status: 'PUBLISHED' }, { method: 'POST', detail: base + `notifications/notices/${notice.id}` })
  }
  const noticeIds = new Set(Object.entries(c.state.operations).filter(([key]) => /^notice:\d+$/.test(key)).map(([, op]) => op.id))
  for (let i = 0; i < 40; i++) {
    const email = `student.${String(i + 1).padStart(3, '0')}@example.test`
    const inbox = await c.list(email, '/api/v1/notifications', true)
    const delivery = inbox.filter(item => noticeIds.has(item.delivery.noticeId)).sort((a, b) => a.delivery.id.localeCompare(b.delivery.id))[0]?.delivery
    if (!delivery) throw new DemoError('Expected demo inbox delivery is missing.')
    await c.change(`delivery:read:${i}`, email, `/api/v1/notifications/${delivery.id}/read`, {}, { status: 'READ' }, {
      detail: `/api/v1/notifications/${delivery.id}`, unwrap: item => item.delivery,
    })
  }
  const eventRows = []
  for (let i = 0; i < 16; i++) {
    const date = new Date(c.state.fixtureDate + 'T09:00:00Z'); date.setUTCDate(date.getUTCDate() + 7 + i)
    let event = await c.create(`event:${i}`, 'EVENT_ADMIN', base + 'events', {
      code: code('EVENT', i), title: events[i], description: `Sự kiện demo ${events[i]} dành cho sinh viên. Dữ liệu hoàn toàn giả lập.`,
      startsAt: date.toISOString(), endsAt: new Date(date.getTime() + 3 * 3600000).toISOString(), capacity: 40,
    })
    event = await c.change(`event:open:${i}`, 'EVENT_ADMIN', base + `events/${event.id}`, { ...fields(event, ['code', 'title', 'description', 'startsAt', 'endsAt', 'capacity']), status: 'OPEN' }, { status: 'OPEN' }); eventRows.push(event)
    for (let j = 0; j < 25; j++) {
      const studentId = students[(i * 17 + j) % 200].id
      const registration = await c.create(`event:membership:${i}:${j}`, 'EVENT_ADMIN', base + `events/${event.id}/registrations`, { studentId }, {
        list: base + 'event-registrations', match: { studentId, eventId: event.id },
      })
      if (j < 5) await c.change(`event:cancel:${i}:${j}`, 'EVENT_ADMIN', base + `event-registrations/${registration.id}`, { action: 'CANCEL' }, { status: 'CANCELLED' })
      if (j === 0) await c.change(`event:restore:${i}:${j}`, 'EVENT_ADMIN', base + `event-registrations/${registration.id}`, { action: 'RESTORE' }, { status: 'REGISTERED' })
      if (j >= 20) await c.change(`event:attend:${i}:${j}`, 'EVENT_ADMIN', base + `event-registrations/${registration.id}`, { action: 'ATTEND' }, { status: 'ATTENDED' })
    }
  }
  for (let i = 12; i < 16; i++) await c.change(`event:close:${i}`, 'EVENT_ADMIN', base + `events/${eventRows[i].id}`, { ...fields(eventRows[i], ['code', 'title', 'description', 'startsAt', 'endsAt', 'capacity']), status: 'CLOSED' }, { status: 'CLOSED' })
  console.log('24 publications/1200 deliveries and 16 events/400 retained memberships verified.')

  const titles = [], copies = []
  for (let i = 0; i < 120; i++) titles.push(await c.create(`title:${i}`, 'LIBRARY_ADMIN', base + 'library/titles', {
    code: code('BOOK', i), title: `${['Giáo trình', 'Chuyên khảo'][i % 2]} ${subjects[i % 5]} ${units[Math.floor(i / 10)]} tập ${i % 10 + 1}`,
    author: names[i % names.length],
  }))
  for (let i = 0; i < 360; i++) copies.push(await c.create(`copy:${i}`, 'LIBRARY_ADMIN', base + 'library/copies', { code: code('COPY', i), titleId: titles[Math.floor(i / 3)].id }))
  for (let i = 0; i < 200; i++) {
    const loan = await c.create(`loan:${i}`, 'LIBRARY_ADMIN', base + 'library/loans', { copyId: copies[i].id, studentId: students[i].id })
    if (i < 80) await c.change(`loan:return:${i}`, 'LIBRARY_ADMIN', base + `library/loans/${loan.id}/return`, {}, { status: 'RETURNED' }, { detail: base + `library/loans/${loan.id}` })
  }
  console.log('Library: 120 titles/360 copies, 120 open and 80 returned loans verified.')
}

export async function verifyInventory(c) {
  const resources = [
    ['units', 'ORGANIZATION_ADMIN', 'organization-units', 'unit:', 16],
    ['students', 'STUDENT_ADMIN', 'students', 'student:', 200],
    ['personnel', 'PERSONNEL_ADMIN', 'faculty-staff', 'personnel:', 80],
    ['programs', 'ACADEMIC_ADMIN', 'academic/programs', 'program:', 12],
    ['courses', 'ACADEMIC_ADMIN', 'academic/courses', 'course:', 60],
    ['terms', 'ACADEMIC_ADMIN', 'academic/terms', 'term:', 3],
    ['offerings', 'ACADEMIC_ADMIN', 'academic/offerings', 'offering:', 90],
    ['sections', 'ACADEMIC_ADMIN', 'academic/sections', 'section:', 150],
    ['enrollments', 'ACADEMIC_ADMIN', 'academic/enrollments', 'enrollment:', 800],
    ['buildings', 'DORMITORY_ADMIN', 'dormitory/buildings', 'building:', 4],
    ['rooms', 'DORMITORY_ADMIN', 'dormitory/rooms', 'room:', 80],
    ['beds', 'DORMITORY_ADMIN', 'dormitory/beds', 'bed:', 240],
    ['assignments', 'DORMITORY_ADMIN', 'dormitory/assignments', 'stay:', 120],
    ['fees', 'FINANCE_ADMIN', 'finance/fees', 'fee:', 6],
    ['charges', 'FINANCE_ADMIN', 'finance/charges', 'charge:', 600],
    ['payments', 'FINANCE_ADMIN', 'finance/payments', 'payment:', 450],
    ['templates', 'NOTIFICATION_ADMIN', 'notifications/templates', 'template:', 24],
    ['notices', 'NOTIFICATION_ADMIN', 'notifications/notices', 'notice:', 24],
    ['events', 'EVENT_ADMIN', 'events', 'event:', 16],
    ['memberships', 'EVENT_ADMIN', 'event-registrations', 'event:membership:', 400],
    ['titles', 'LIBRARY_ADMIN', 'library/titles', 'title:', 120],
    ['copies', 'LIBRARY_ADMIN', 'library/copies', 'copy:', 360],
    ['loans', 'LIBRARY_ADMIN', 'library/loans', 'loan:', 200],
  ]
  const report = {}, records = {}
  for (const [name, role, path, prefix, expected] of resources) {
    const ids = new Set(Object.entries(c.state.operations).filter(([k, v]) => k.startsWith(prefix) && !k.slice(prefix.length).includes(':') && v.done).map(([, v]) => v.id))
    if (name === 'memberships') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^event:membership:\d+:\d+$/.test(key) && op.done) ids.add(op.id)
    }
    if (name === 'assignments') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^stay:(history|current):\d+$/.test(key) && op.done) ids.add(op.id)
    }
    const allRows = await c.list(role, base + path, true)
    const rows = allRows.filter(row => ids.has(row.id))
    if (rows.length !== expected || ids.size !== expected) throw new DemoError(`Persisted ${name} inventory does not match the approved target ${expected}.`)
    records[name] = rows; report[name] = { count: rows.length, totalOnTarget: allRows.length, statuses: {} }
    for (const row of rows) if (row.status) report[name].statuses[row.status] = (report[name].statuses[row.status] ?? 0) + 1
  }
  const linked = new Set(records.students.map(s => s.identityUserId))
  if (linked.size !== 200 || linked.has(null)) throw new DemoError('Student account links are incomplete or duplicated.')
  const accounts = await c.list('ADMIN', base + 'users', true)
  if (accounts.filter(a => a.roles.includes('ADMIN')).length !== 2) throw new DemoError('Expected exactly two demo global administrators.')
  for (const role of scoped) {
    const matches = accounts.filter(a => a.email === `demo.${role.toLowerCase()}@example.test`)
    if (matches.length !== 1 || matches[0].roles.length !== 1 || matches[0].roles[0] !== role) throw new DemoError('A scoped demo role assignment is missing or overprivileged.')
  }
  if (accounts.filter(a => linked.has(a.id) && a.roles.length === 1 && a.roles[0] === 'USER').length !== 200) throw new DemoError('Student accounts have unexpected roles.')
  report.accounts = { globalAdministrators: 2, scopedOrViewerAccounts: 11, linkedStudentUsers: 200 }
  for (const [name, expected] of Object.entries({
    sections: { OPEN: 120, CLOSED: 10, DRAFT: 10, CANCELLED: 10 }, enrollments: { ENROLLED: 740, WITHDRAWN: 60 },
    assignments: { ASSIGNED: 100, RELEASED: 20 }, charges: { OPEN: 580, CANCELLED: 20 }, payments: { RECORDED: 420, REVERSED: 30 },
    notices: { PUBLISHED: 24 }, events: { OPEN: 12, CLOSED: 4 }, memberships: { REGISTERED: 256, CANCELLED: 64, ATTENDED: 80 },
    loans: { OPEN: 120, RETURNED: 80 },
  })) {
    if (Object.keys(report[name].statuses).length !== Object.keys(expected).length
      || Object.entries(expected).some(([status, count]) => report[name].statuses[status] !== count)) throw new DemoError(`Persisted ${name} lifecycle distribution is incorrect.`)
  }
  const refs = (rows, field, parent) => {
    const ids = new Set(parent.map(v => v.id))
    if (rows.some(v => !ids.has(v[field]))) throw new DemoError('A demo owner relationship points outside its fixture inventory.')
  }
  refs(records.students, 'organizationUnitId', records.units); refs(records.personnel, 'organizationUnitId', records.units)
  refs(records.offerings, 'termId', records.terms); refs(records.offerings, 'courseId', records.courses)
  refs(records.sections, 'offeringId', records.offerings); refs(records.enrollments, 'studentId', records.students); refs(records.enrollments, 'sectionId', records.sections)
  refs(records.rooms, 'parentId', records.buildings); refs(records.beds, 'parentId', records.rooms)
  refs(records.assignments, 'studentId', records.students); refs(records.assignments, 'bedId', records.beds)
  refs(records.charges, 'studentId', records.students); refs(records.charges, 'feeId', records.fees); refs(records.payments, 'chargeId', records.charges)
  refs(records.memberships, 'studentId', records.students); refs(records.memberships, 'eventId', records.events)
  refs(records.copies, 'titleId', records.titles); refs(records.loans, 'copyId', records.copies); refs(records.loans, 'studentId', records.students)
  const stays = records.assignments.filter(a => a.status === 'ASSIGNED')
  if (stays.length !== 100 || new Set(stays.map(a => a.bedId)).size !== 100 || new Set(stays.map(a => a.studentId)).size !== 100) throw new DemoError('Current accommodation uniqueness failed.')
  if (records.loans.some(l => Date.parse(l.dueAt) - Date.parse(l.borrowedAt) !== 14 * 86400000)) throw new DemoError('Library default due interval failed.')
  const openLoans = records.loans.filter(l => l.status === 'OPEN')
  if (openLoans.length !== 120 || new Set(openLoans.map(l => l.copyId)).size !== 120) throw new DemoError('Open loan copy uniqueness failed.')
  for (const section of records.sections) {
    if (records.enrollments.filter(e => e.sectionId === section.id && e.status === 'ENROLLED').length > section.capacity) throw new DemoError('Enrollment capacity exceeded.')
  }
  if (new Set(records.enrollments.map(e => `${e.studentId}:${e.sectionId}`)).size !== 800) throw new DemoError('Duplicate retained enrollment pair.')
  for (const event of records.events) {
    if (records.memberships.filter(m => m.eventId === event.id && m.status !== 'CANCELLED').length > event.capacity) throw new DemoError('Event capacity exceeded.')
  }
  for (const charge of records.charges) {
    const balance = await c.request('FINANCE_ADMIN', 'GET', base + `finance/charges/${charge.id}/balance`)
    const paid = records.payments.filter(p => p.chargeId === charge.id && p.status === 'RECORDED').reduce((sum, p) => sum + p.amount, 0)
    if (![balance.amount, balance.paidAmount, balance.outstandingAmount, paid].every(Number.isSafeInteger)
      || balance.currency !== 'VND' || balance.paidAmount !== paid
      || balance.outstandingAmount !== (charge.status === 'CANCELLED' ? 0 : charge.amount - paid)) throw new DemoError('Receipt totals and charge balance do not reconcile.')
  }
  let deliveries = 0, readDeliveries = 0
  for (let i = 0; i < 200; i++) {
    const email = `student.${String(i + 1).padStart(3, '0')}@example.test`
    const session = await c.session(email)
    if (!linked.has(session.user.id)) throw new DemoError('Student login does not match its linked account.')
    const inbox = await c.list(email, '/api/v1/notifications', true)
    const own = inbox.filter(item => records.notices.some(n => n.id === item.delivery.noticeId))
    if (own.some(item => item.delivery.recipientId !== session.user.id)) throw new DemoError('Notification recipient ownership mismatch.')
    deliveries += own.length; readDeliveries += own.filter(item => item.delivery.status === 'READ').length
  }
  if (deliveries !== 1200) throw new DemoError('Persisted notification delivery count does not match 1200.')
  if (readDeliveries !== 40) throw new DemoError('Expected 40 read demo deliveries with retained unread examples.')
  report.deliveries = { count: deliveries, read: readDeliveries, unread: deliveries - readDeliveries }
  report.auditCoverage = {}
  for (const [role, source, names] of [
    ['ORGANIZATION_ADMIN', 'PEOPLE', ['units']], ['STUDENT_ADMIN', 'PEOPLE', ['students']], ['PERSONNEL_ADMIN', 'PEOPLE', ['personnel']],
    ['ACADEMIC_ADMIN', 'ACADEMIC', ['programs', 'courses', 'terms', 'offerings', 'sections', 'enrollments']],
    ['DORMITORY_ADMIN', 'DORMITORY', ['buildings', 'rooms', 'beds', 'assignments']], ['FINANCE_ADMIN', 'FINANCE', ['fees', 'charges', 'payments']],
    ['NOTIFICATION_ADMIN', 'NOTIFICATION', ['templates', 'notices']], ['EVENT_ADMIN', 'EVENT', ['events', 'memberships']],
    ['LIBRARY_ADMIN', 'LIBRARY', ['titles', 'copies', 'loans']],
  ]) {
    const actor = (await c.session(role)).user.id
    const entries = await c.list('AUDIT_VIEWER', `${base}audits/${source}?actorId=${actor}`, true)
    const covered = new Set(entries.filter(entry => entry.actorId === actor).map(entry => entry.targetId))
    const targets = names.flatMap(name => records[name].map(row => row.id))
    if (targets.some(id => !covered.has(id))) throw new DemoError(`Owner audit coverage is incomplete for ${role}.`)
    report.auditCoverage[role] = { fixtureTargets: targets.length, retainedEntries: entries.length }
  }
  const dashboard = await c.request('REPORTING_VIEWER', 'GET', base + 'reports/dashboard')
  if (dashboard.currency !== 'VND' || Object.keys(dashboard.groups).sort().join(',') !== 'ACADEMIC,DORMITORY,EVENT,FINANCE,IDENTITY,LIBRARY,NOTIFICATION,PEOPLE') throw new DemoError('Reporting dashboard owner coverage failed.')
  report.dashboard = { groups: Object.keys(dashboard.groups), currency: dashboard.currency }
  report.ownerWritesThisRun = c.writes; report.verifiedAt = new Date().toISOString()
  await writeJson(join(c.directory, 'inventory.json'), report)
  console.log(JSON.stringify(report, null, 2))
  return report
}

async function main() {
  const position = process.argv.indexOf('--state-dir')
  if (position < 0 || !process.argv[position + 1]) throw new DemoError('Usage: node scripts/seed-demo.mjs --state-dir PRIVATE_DEMO_DIRECTORY')
  const directory = resolve(process.argv[position + 1])
  const config = await readJson(join(directory, 'config.json')), accounts = await readJson(join(directory, 'accounts.json'))
  const binding = createHash('sha256').update(config.issuer + config.audience + config.jwtSecret).digest('hex')
  let state
  try { state = await readJson(join(directory, 'journal.json')) }
  catch (error) { if (error.code !== 'ENOENT') throw error; state = { binding, fixtureVersion: 1, fixtureDate: new Date().toISOString().slice(0, 10), operations: {} } }
  if (state.binding !== binding || state.fixtureVersion !== config.fixtureVersion) throw new DemoError('Journal/configuration binding or fixture version mismatch.')
  const lock = join(directory, 'seed.lock')
  let handle
  try { handle = await open(lock, 'wx', 0o600) }
  catch { throw new DemoError('A seed lock exists. Confirm no loader is running before resolving a stale lock; do not run two loaders.') }
  try {
    await handle.writeFile(JSON.stringify({ pid: process.pid, startedAt: new Date().toISOString() }))
    const client = new DemoClient(config, accounts, state, directory)
    await seedInventory(client); await verifyInventory(client)
  }
  finally { await handle.close(); await unlink(lock) }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => {
    console.error(error instanceof DemoError ? error.message : 'Demo loader stopped unexpectedly; credentials and response bodies are suppressed. Inspect private journal/configuration before retrying.')
    process.exitCode = 1
  })
}
