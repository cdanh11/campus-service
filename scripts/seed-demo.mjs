import { createHash } from 'node:crypto'
import { open, unlink } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { DemoClient, DemoError, readJson, writeJson } from './demo-client.mjs'

export const ADMIN_PASSWORD = 'admin@AA1234'
export const STUDENT_PASSWORD = 'sv@SV1231234'
export const demoPassword = roles => roles.length === 1 && roles[0] === 'USER' ? STUDENT_PASSWORD : ADMIN_PASSWORD
const fields = (value, keys) => Object.fromEntries(keys.map(key => [key, value[key]]))
const base = '/api/v1/admin/'
const code = (kind, i) => `CD-${kind}-${String(i + 1).padStart(3, '0')}`
const names = ['Nguyễn Minh An', 'Trần Ngọc Anh', 'Lê Hoàng Bảo', 'Phạm Gia Bình', 'Hoàng Thanh Chi', 'Vũ Hải Dương', 'Đặng Quỳnh Giang', 'Bùi Khánh Hà', 'Đỗ Trung Hiếu', 'Hồ Mai Hương', 'Ngô Tuấn Khang', 'Dương Thảo Linh', 'Lý Đức Minh', 'Phan Bảo Ngọc', 'Võ Hải Phong', 'Trịnh Minh Quân', 'Đinh Thu Trang', 'Mai Bảo Trâm', 'Tạ Nhật Vy', 'Cao Anh Vũ']
const units = ['Công nghệ thông tin', 'Kinh tế', 'Ngoại ngữ', 'Kỹ thuật điện', 'Cơ khí', 'Xây dựng', 'Môi trường', 'Luật', 'Truyền thông', 'Du lịch', 'Khoa học cơ bản', 'Quản trị kinh doanh', 'Đào tạo', 'Công tác sinh viên', 'Tài chính', 'Hành chính nhân sự']
const subjects = ['Nhập môn ngành', 'Phương pháp nghiên cứu', 'Thực hành chuyên ngành', 'Ứng dụng dữ liệu', 'Đồ án cơ sở']
const events = ['Ngày hội việc làm', 'Tuần lễ định hướng', 'Cuộc thi lập trình', 'Hội thảo khởi nghiệp', 'Hiến máu tình nguyện', 'Ngày hội văn hóa', 'Giải chạy sinh viên', 'Tọa đàm doanh nghiệp', 'Triển lãm đồ án', 'Kỹ năng phỏng vấn', 'Ngày hội đọc sách', 'Bảo vệ môi trường', 'Sinh viên nghiên cứu khoa học', 'Giao lưu câu lạc bộ', 'Hội thảo học bổng', 'Kết nối cựu sinh viên']
const scoped = ['ACADEMIC_AFFAIRS', 'CAMPUS_SERVICES', 'FINANCE', 'AUDITOR']
const meetings = [[2, '07:30', '09:30'], [2, '09:45', '11:45'], [3, '13:00', '15:00'], [4, '07:30', '09:30'], [5, '15:15', '17:15'], [6, '09:45', '11:45'], [7, '13:00', '15:00']]
const meeting = (i) => {
  const [weekday, startsAt, endsAt] = meetings[i % meetings.length]
  return { weekday, startsAt, endsAt, room: `P.${101 + (i % 30)}` }
}

async function account(client, email, displayName, roles) {
  let value = client.accounts.accounts.find(a => a.email === email)
  if (!value) {
    value = { email, displayName, roles, password: demoPassword(roles) }
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
  console.log('Verified global and department demo accounts; credential values are private.')

  const org = [], students = [], users = [], personnel = [], programs = [], courses = [], terms = [], offerings = [], sections = []
  for (let i = 0; i < 16; i++) org.push(await c.create(`unit:${i}`, 'ACADEMIC_AFFAIRS', base + 'organization-units', {
    code: code('UNIT', i), name: `${i < 12 ? 'Khoa' : 'Phòng'} ${units[i]}`, unitType: i < 12 ? 'FACULTY' : 'ADMINISTRATIVE', status: 'ACTIVE',
  }))
  for (let i = 0; i < 200; i++) {
    const email = `student.${String(i + 1).padStart(3, '0')}@example.test`
    const name = `${names[i % names.length]} ${String(Math.floor(i / names.length) + 1).padStart(2, '0')}`
    const user = await account(c, email, name, ['USER']); users.push(user)
    students.push(await c.create(`student:${i}`, 'ACADEMIC_AFFAIRS', base + 'students', {
      studentNumber: `CD2026${String(i + 1).padStart(4, '0')}`, fullName: name, email,
      identityUserId: user.id, organizationUnitId: org[i % 12].id, status: 'ACTIVE',
    }))
    if ((i + 1) % 50 === 0) console.log(`Student profiles verified: ${i + 1}/200`)
  }
  for (let i = 0; i < 80; i++) personnel.push(await c.create(`personnel:${i}`, 'ACADEMIC_AFFAIRS', base + 'faculty-staff', {
    personnelNumber: code('EMP', i), fullName: `${names[(i + 7) % names.length]} GV${Math.floor(i / names.length) + 1}`,
    email: `employee.${i + 1}@example.test`, identityUserId: null, personnelType: i < 60 ? 'FACULTY' : 'STAFF',
    organizationUnitId: org[i < 60 ? i % 12 : 12 + i % 4].id, status: 'ACTIVE',
  }))
  for (let i = 0; i < 12; i++) programs.push(await c.create(`program:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/programs', {
    code: code('PROG', i), name: `Cử nhân ${units[i]}`, organizationUnitId: org[i].id, status: 'ACTIVE',
  }))
  for (let i = 0; i < 60; i++) courses.push(await c.create(`course:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/courses', {
    code: code('COURSE', i), title: `${subjects[i % 5]} ${units[Math.floor(i / 5)]}`, credits: i % 5 === 2 ? 2 : 3,
    organizationUnitId: org[Math.floor(i / 5)].id, status: 'ACTIVE',
    courseGroup: 'MAJOR', hasFinalProject: i % 5 === 4,
  }))
  for (let i = 0; i < 10; i++) courses.push(await c.create(`course:general:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/courses', {
    code: code('COURSE', 60 + i), title: `${subjects[(i + 2) % 5]} đại cương ${i + 1}`, credits: i % 3 === 2 ? 2 : 3,
    courseGroup: 'GENERAL', organizationUnitId: org[10 + i % 2].id, status: 'ACTIVE',
  }))
  const termDates = [['2025-09-01', '2026-01-15'], ['2026-02-01', '2026-06-15'], ['2026-09-01', '2027-01-15']]
  for (let i = 0; i < 3; i++) {
    let term = await c.create(`term:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/terms', {
      code: code('TERM', i), name: `Học kỳ demo ${i + 1}`, startDate: termDates[i][0], endDate: termDates[i][1],
    })
    term = await c.change(`term:active:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/terms/${term.id}`, { ...fields(term, ['code', 'name', 'startDate', 'endDate']), status: 'ACTIVE' }, { status: 'ACTIVE' })
    terms.push(term)
  }
  for (let i = 0; i < 200; i++) {
    const cohort = i === 0 ? 'K23' : `K${21 + i % 6}`, programId = programs[i % 12].id
    await c.change(`student:profile:${i}`, 'ACADEMIC_AFFAIRS', base + `students/${students[i].id}/profile`, { cohort, programId },
      { cohort, programId }, { versionField: 'profileVersion' })
  }
  for (let i = 0; i < 90; i++) {
    let offering = await c.create(`offering:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/offerings', { termId: terms[Math.floor(i / 30)].id, courseId: courses[i % 60].id })
    offering = await c.change(`offering:open:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/offerings/${offering.id}`, { status: 'OPEN' }, { status: 'OPEN' }); offerings.push(offering)
  }
  for (let i = 0; i < 150; i++) {
    let section = await c.create(`section:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/sections', {
      offeringId: offerings[i % 90].id, code: code('CLASS', i), capacity: 40, facultyId: i < 140 ? personnel[i % 60].id : null,
    })
    if (i < 130) section = await c.change(`section:open:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/sections/${section.id}`, { ...fields(section, ['code', 'capacity', 'facultyId']), ...meeting(i), status: 'OPEN' }, { status: 'OPEN' })
    if (i >= 140) section = await c.change(`section:cancel:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/sections/${section.id}`, { ...fields(section, ['code', 'capacity', 'facultyId']), status: 'CANCELLED' }, { status: 'CANCELLED' })
    sections.push(section)
  }
  const enrollments = []
  for (let i = 0; i < 800; i++) {
    const student = students[Math.floor(i / 4)], section = sections[(Math.floor(i / 4) * 7 + (i % 4) * 31) % 120]
    const enrollment = await c.create(`enrollment:${i}`, 'ACADEMIC_AFFAIRS', base + 'academic/enrollments', { studentId: student.id, sectionId: section.id })
    enrollments.push(enrollment)
    if (i % 10 === 0) await c.change(`enrollment:withdraw:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/enrollments/${enrollment.id}`, { status: 'WITHDRAWN' }, { status: 'WITHDRAWN' })
    if (i % 40 === 0) await c.change(`enrollment:restore:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/enrollments/${enrollment.id}`, { status: 'ENROLLED' }, { status: 'ENROLLED' })
  }
  for (let i = 120; i < 130; i++) await c.change(`section:close:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/sections/${sections[i].id}`, { ...fields(sections[i], ['code', 'capacity', 'facultyId']), status: 'CLOSED' }, { status: 'CLOSED' })

  const retakes = new Map()
  for (let i = 0; i < 400; i++) {
    const q = Math.floor(i / 4), s = (q * 7 + (i % 4) * 31) % 120
    const termIndex = Math.floor((s % 90) / 30)
    if (termIndex > 1 || (i % 10 === 0 && i % 40 !== 0)) continue
    const course = courses[(s % 90) % 60]
    const failed = termIndex === 0 && i % 11 === 4
    const low = termIndex === 0 && i % 13 === 6 && !failed
    const scores = failed ? { attendanceScore: 6, midtermScore: 2, ...(course.hasFinalProject ? { projectScore: 3 } : { finalScore: 3 }) }
      : low ? { attendanceScore: 6, midtermScore: 4, ...(course.hasFinalProject ? { projectScore: 4.5 } : { finalScore: 4.5 }) }
        : { attendanceScore: 6 + (i % 5) * 0.5, midtermScore: 4.5 + ((i * 3) % 50) / 10,
            ...(course.hasFinalProject ? { projectScore: 4.5 + ((i * 7) % 50) / 10 } : { finalScore: 4.5 + ((i * 7) % 50) / 10 }) }
    const enrollment = enrollments[i]
    await c.create(`grade:${i}`, 'ACADEMIC_AFFAIRS', base + `academic/enrollments/${enrollment.id}/grade`, scores,
      { list: base + 'academic/grades?enrollmentId=' + enrollment.id, match: { enrollmentId: enrollment.id } })
    if ((failed || low) && !retakes.has(`${q}:${course.id}`)) retakes.set(`${q}:${course.id}`, { student: students[q], course })
  }

  for (let p = 0; p < 10; p++) {
    const course = courses[60 + (3 * p) % 10]
    const offering = await c.create(`offering:general:${p}`, 'ACADEMIC_AFFAIRS', base + 'academic/offerings', { termId: terms[1].id, courseId: course.id })
    const open = await c.change(`offering:general:open:${p}`, 'ACADEMIC_AFFAIRS', base + `academic/offerings/${offering.id}`, { status: 'OPEN' }, { status: 'OPEN' })
    const section = await c.create(`section:general:${p}`, 'ACADEMIC_AFFAIRS', base + 'academic/sections', {
      offeringId: open.id, code: code('GEN', p), capacity: 40, facultyId: personnel[p].id,
    })
    const opened = await c.change(`section:general:open:${p}`, 'ACADEMIC_AFFAIRS', base + `academic/sections/${section.id}`, {
      ...fields(section, ['code', 'capacity', 'facultyId']), ...meeting(p), status: 'OPEN',
    }, { status: 'OPEN' })
    const enrollment = await c.create(`enrollment:general:${p}`, 'ACADEMIC_AFFAIRS', base + 'academic/enrollments', { studentId: students[p].id, sectionId: opened.id })
    await c.create(`grade:general:${p}`, 'ACADEMIC_AFFAIRS', base + `academic/enrollments/${enrollment.id}/grade`,
      { attendanceScore: 8, midtermScore: 7, finalScore: 8.5 },
      { list: base + 'academic/grades?enrollmentId=' + enrollment.id, match: { enrollmentId: enrollment.id } })
  }

  for (let p = 0; p < 12; p++) {
    const general = [0, 1, 2].map(n => courses[60 + (3 * p + n) % 10]), major = [0, 1, 2, 3].map(n => courses[5 * p + n])
    for (const [course, semesterIndex, prerequisite] of [
      [general[0], 1, null], [general[1], 2, general[0]], [general[2], 3, general[1]],
      [major[0], 4, general[2]], [major[1], 5, major[0]], [major[2], 6, major[1]], [major[3], 7, major[2]],
    ]) await c.create(`curriculum:${p}:${semesterIndex}`, 'ACADEMIC_AFFAIRS', base + 'academic/curriculum', {
      programId: programs[p].id, courseId: course.id, semesterIndex,
      ...(prerequisite ? { prerequisiteCourseId: prerequisite.id } : {}),
    }, { list: base + 'academic/curriculum?programId=' + programs[p].id, match: { courseId: course.id } })
  }

  const retakeSections = new Map()
  let retakeIndex = 0
  for (const { student, course } of retakes.values()) {
    let retakeSection = retakeSections.get(course.id)
    if (!retakeSection) {
      const k = retakeSections.size
      const offering = await c.create(`offering:retake:${k}`, 'ACADEMIC_AFFAIRS', base + 'academic/offerings', { termId: terms[1].id, courseId: course.id })
      const open = await c.change(`offering:retake:open:${k}`, 'ACADEMIC_AFFAIRS', base + `academic/offerings/${offering.id}`, { status: 'OPEN' }, { status: 'OPEN' })
      const section = await c.create(`section:retake:${k}`, 'ACADEMIC_AFFAIRS', base + 'academic/sections', {
        offeringId: open.id, code: code('RET', k), capacity: 40, facultyId: personnel[k % 60].id,
      })
      retakeSection = await c.change(`section:retake:open:${k}`, 'ACADEMIC_AFFAIRS', base + `academic/sections/${section.id}`, {
        ...fields(section, ['code', 'capacity', 'facultyId']), ...meeting(k + 3), status: 'OPEN',
      }, { status: 'OPEN' })
      retakeSections.set(course.id, retakeSection)
    }
    const n = retakeIndex++
    const enrollment = await c.create(`enrollment:retake:${n}`, 'ACADEMIC_AFFAIRS', base + 'academic/enrollments',
      { studentId: student.id, sectionId: retakeSection.id })
    await c.create(`grade:retake:${n}`, 'ACADEMIC_AFFAIRS', base + `academic/enrollments/${enrollment.id}/grade`,
      { attendanceScore: 8, midtermScore: 7.5, ...(course.hasFinalProject ? { projectScore: 8 } : { finalScore: 8 }) },
      { list: base + 'academic/grades?enrollmentId=' + enrollment.id, match: { enrollmentId: enrollment.id } })
  }
  console.log('Academic catalog, delivery, cohorts, curriculum and 318 grades verified.');

  const buildings = [], rooms = [], beds = []
  for (let i = 0; i < 4; i++) buildings.push(await c.create(`building:${i}`, 'CAMPUS_SERVICES', base + 'dormitory/buildings', { code: code('BLD', i), name: `Ký túc xá ${String.fromCharCode(65 + i)}` }))
  for (let i = 0; i < 80; i++) rooms.push(await c.create(`room:${i}`, 'CAMPUS_SERVICES', base + 'dormitory/rooms', {
    code: code('ROOM', i), name: `Phòng ${Math.floor(i / 20) + 1}-${String(i % 20 + 1).padStart(2, '0')}`, parentId: buildings[Math.floor(i / 20)].id,
  }))
  for (let i = 0; i < 240; i++) beds.push(await c.create(`bed:${i}`, 'CAMPUS_SERVICES', base + 'dormitory/beds', {
    code: code('BED', i), name: `Giường ${i % 3 + 1}`, parentId: rooms[Math.floor(i / 3)].id,
  }))
  for (let i = 0; i < 20; i++) {
    const assignment = await c.create(`stay:history:${i}`, 'CAMPUS_SERVICES', base + 'dormitory/assignments', { studentId: students[100 + i].id, bedId: beds[100 + i].id })
    await c.change(`stay:release:${i}`, 'CAMPUS_SERVICES', base + `dormitory/assignments/${assignment.id}`, { status: 'RELEASED' }, { status: 'RELEASED' })
  }
  for (let i = 0; i < 100; i++) await c.create(`stay:current:${i}`, 'CAMPUS_SERVICES', base + 'dormitory/assignments', { studentId: students[i].id, bedId: beds[i].id })
  console.log('Dormitory inventory and 100 current/20 released assignments verified.')

  const fees = [], charges = [], payments = []
  const feeNames = ['Học phí học kỳ', 'Phí ký túc xá', 'Bảo hiểm sinh viên', 'Phí hoạt động', 'Phí tài liệu', 'Phí thực hành']
  const amounts = [6000000, 1500000, 750000, 200000, 300000, 500000]
  for (let i = 0; i < 6; i++) fees.push(await c.create(`fee:${i}`, 'FINANCE', base + 'finance/fees', { code: code('FEE', i), name: feeNames[i], amount: amounts[i] }))
  for (let i = 0; i < 600; i++) charges.push(await c.create(`charge:${i}`, 'FINANCE', base + 'finance/charges', {
    chargeNumber: code('CHARGE', i), studentId: students[Math.floor(i / 3)].id, feeId: fees[i % 6].id,
    dueDate: i % 3 === 0 ? '2026-09-01' : '2027-01-31',
  }))
  for (let i = 0; i < 450; i++) {
    const chargeIndex = i < 400 ? i : (i - 400) * 2
    const balance = await c.request('FINANCE', 'GET', base + `finance/charges/${charges[chargeIndex].id}/balance`)
    const payment = await c.create(`payment:${i}`, 'FINANCE', base + 'finance/payments', {
      receiptNumber: code('RECEIPT', i), chargeId: charges[chargeIndex].id,
      amount: amounts[chargeIndex % 6] / (i >= 400 || chargeIndex % 2 === 0 ? 2 : 1), expectedChargeVersion: balance.rowVersion,
    }); payments.push(payment)
  }
  for (let i = 0; i < 30; i++) {
    const balance = await c.request('FINANCE', 'GET', base + `finance/charges/${payments[i].chargeId}/balance`)
    await c.change(`payment:reverse:${i}`, 'FINANCE', base + `finance/payments/${payments[i].id}`, {
      status: 'REVERSED', reason: 'Demo: điều chỉnh biên nhận nhập nhầm, giữ lịch sử', expectedChargeVersion: balance.rowVersion,
    }, { status: 'REVERSED' })
  }
  for (let i = 550; i < 570; i++) await c.change(`charge:cancel:${i}`, 'FINANCE', base + `finance/charges/${charges[i].id}`, { status: 'CANCELLED' }, { status: 'CANCELLED' })
  console.log('Finance: 600 snapshots, 450 receipts and retained reversal/cancellation examples verified.')

  for (let i = 0; i < 24; i++) {
    const template = await c.create(`template:${i}`, 'CAMPUS_SERVICES', base + 'notifications/templates', {
      code: code('NOTICE', i), name: `Thông báo demo ${i + 1}`, title: `${['Lịch học', 'Học phí', 'Hoạt động sinh viên', 'Thư viện'][i % 4]} — đợt ${i + 1}`,
      body: `Dữ liệu minh họa đồ án Campus Platform. Thông báo đợt ${i + 1}: vui lòng xem thông tin trên ứng dụng và liên hệ đơn vị phụ trách khi cần hỗ trợ.`,
    })
    const notice = await c.create(`notice:${i}`, 'CAMPUS_SERVICES', base + 'notifications/notices', { templateId: template.id })
    const recipientIds = Array.from({ length: 50 }, (_, j) => users[(i * 25 + j) % 200].id)
    await c.change(`notice:publish:${i}`, 'CAMPUS_SERVICES', base + `notifications/notices/${notice.id}/publish`, { recipientIds }, { status: 'PUBLISHED' }, { method: 'POST', detail: base + `notifications/notices/${notice.id}` })
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
    let event = await c.create(`event:${i}`, 'CAMPUS_SERVICES', base + 'events', {
      code: code('EVENT', i), title: events[i], description: `Sự kiện demo ${events[i]} dành cho sinh viên. Dữ liệu hoàn toàn giả lập.`,
      startsAt: date.toISOString(), endsAt: new Date(date.getTime() + 3 * 3600000).toISOString(), capacity: 40,
    })
    event = await c.change(`event:open:${i}`, 'CAMPUS_SERVICES', base + `events/${event.id}`, { ...fields(event, ['code', 'title', 'description', 'startsAt', 'endsAt', 'capacity']), status: 'OPEN' }, { status: 'OPEN' }); eventRows.push(event)
    for (let j = 0; j < 25; j++) {
      const studentId = students[(i * 17 + j) % 200].id
      const registration = await c.create(`event:membership:${i}:${j}`, 'CAMPUS_SERVICES', base + `events/${event.id}/registrations`, { studentId }, {
        list: base + 'event-registrations', match: { studentId, eventId: event.id },
      })
      if (j < 5) await c.change(`event:cancel:${i}:${j}`, 'CAMPUS_SERVICES', base + `event-registrations/${registration.id}`, { action: 'CANCEL' }, { status: 'CANCELLED' })
      if (j === 0) await c.change(`event:restore:${i}:${j}`, 'CAMPUS_SERVICES', base + `event-registrations/${registration.id}`, { action: 'RESTORE' }, { status: 'REGISTERED' })
      if (j >= 20) await c.change(`event:attend:${i}:${j}`, 'CAMPUS_SERVICES', base + `event-registrations/${registration.id}`, { action: 'ATTEND' }, { status: 'ATTENDED' })
    }
  }
  for (let i = 12; i < 16; i++) await c.change(`event:close:${i}`, 'CAMPUS_SERVICES', base + `events/${eventRows[i].id}`, { ...fields(eventRows[i], ['code', 'title', 'description', 'startsAt', 'endsAt', 'capacity']), status: 'CLOSED' }, { status: 'CLOSED' })
  console.log('24 publications/1200 deliveries and 16 events/400 retained memberships verified.')

  const titles = [], copies = []
  for (let i = 0; i < 120; i++) titles.push(await c.create(`title:${i}`, 'CAMPUS_SERVICES', base + 'library/titles', {
    code: code('BOOK', i), title: `${['Giáo trình', 'Chuyên khảo'][i % 2]} ${subjects[i % 5]} ${units[Math.floor(i / 10)]} tập ${i % 10 + 1}`,
    author: names[i % names.length],
  }))
  for (let i = 0; i < 360; i++) copies.push(await c.create(`copy:${i}`, 'CAMPUS_SERVICES', base + 'library/copies', { code: code('COPY', i), titleId: titles[Math.floor(i / 3)].id }))
  for (let i = 0; i < 200; i++) {
    const loan = await c.create(`loan:${i}`, 'CAMPUS_SERVICES', base + 'library/loans', { copyId: copies[i].id, studentId: students[i].id })
    if (i < 80) await c.change(`loan:return:${i}`, 'CAMPUS_SERVICES', base + `library/loans/${loan.id}/return`, {}, { status: 'RETURNED' }, { detail: base + `library/loans/${loan.id}` })
  }
  console.log('Library: 120 titles/360 copies, 120 open and 80 returned loans verified.')
}

export async function verifyInventory(c) {
  const resources = [
    ['units', 'ACADEMIC_AFFAIRS', 'organization-units', 'unit:', 16],
    ['students', 'ACADEMIC_AFFAIRS', 'students', 'student:', 200],
    ['personnel', 'ACADEMIC_AFFAIRS', 'faculty-staff', 'personnel:', 80],
    ['programs', 'ACADEMIC_AFFAIRS', 'academic/programs', 'program:', 12],
    ['courses', 'ACADEMIC_AFFAIRS', 'academic/courses', 'course:', 70],
    ['terms', 'ACADEMIC_AFFAIRS', 'academic/terms', 'term:', 3],
    ['offerings', 'ACADEMIC_AFFAIRS', 'academic/offerings', 'offering:', 124],
    ['sections', 'ACADEMIC_AFFAIRS', 'academic/sections', 'section:', 184],
    ['enrollments', 'ACADEMIC_AFFAIRS', 'academic/enrollments', 'enrollment:', 840],
    ['grades', 'ACADEMIC_AFFAIRS', 'academic/grades', 'grade:', 318],
    ['buildings', 'CAMPUS_SERVICES', 'dormitory/buildings', 'building:', 4],
    ['rooms', 'CAMPUS_SERVICES', 'dormitory/rooms', 'room:', 80],
    ['beds', 'CAMPUS_SERVICES', 'dormitory/beds', 'bed:', 240],
    ['assignments', 'CAMPUS_SERVICES', 'dormitory/assignments', 'stay:', 120],
    ['fees', 'FINANCE', 'finance/fees', 'fee:', 6],
    ['charges', 'FINANCE', 'finance/charges', 'charge:', 600],
    ['payments', 'FINANCE', 'finance/payments', 'payment:', 450],
    ['templates', 'CAMPUS_SERVICES', 'notifications/templates', 'template:', 24],
    ['notices', 'CAMPUS_SERVICES', 'notifications/notices', 'notice:', 24],
    ['events', 'CAMPUS_SERVICES', 'events', 'event:', 16],
    ['memberships', 'CAMPUS_SERVICES', 'event-registrations', 'event:membership:', 400],
    ['titles', 'CAMPUS_SERVICES', 'library/titles', 'title:', 120],
    ['copies', 'CAMPUS_SERVICES', 'library/copies', 'copy:', 360],
    ['loans', 'CAMPUS_SERVICES', 'library/loans', 'loan:', 200],
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
    if (name === 'courses') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^course:general:\d+$/.test(key) && op.done) ids.add(op.id)
    }
    if (name === 'offerings') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^offering:(general|retake):\d+$/.test(key) && op.done) ids.add(op.id)
    }
    if (name === 'sections') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^section:(general|retake):\d+$/.test(key) && op.done) ids.add(op.id)
    }
    if (name === 'enrollments') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^enrollment:(general|retake):\d+$/.test(key) && op.done) ids.add(op.id)
    }
    if (name === 'grades') {
      for (const [key, op] of Object.entries(c.state.operations)) if (/^grade:(general|retake):\d+$/.test(key) && op.done) ids.add(op.id)
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
  report.accounts = { globalAdministrators: 2, departmentAccounts: 4, linkedStudentUsers: 200 }
  for (const [name, expected] of Object.entries({
    sections: { OPEN: 154, CLOSED: 10, DRAFT: 10, CANCELLED: 10 }, enrollments: { ENROLLED: 780, WITHDRAWN: 60 },
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
  refs(records.grades, 'enrollmentId', records.enrollments)
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
  if (new Set(records.enrollments.map(e => `${e.studentId}:${e.sectionId}`)).size !== 840) throw new DemoError('Duplicate retained enrollment pair.')
  const round2 = value => Math.round(value * 100) / 100
  const cohortOf = index => index === 0 ? 'K23' : `K${21 + index % 6}`
  const studentsByIndex = new Map(records.students.map(student => [Number(student.studentNumber.slice(6)) - 1, student]))
  const programsByCode = new Map(records.programs.map(program => [program.code, program])), cohorts = {}
  for (let i = 0; i < 200; i++) {
    const student = studentsByIndex.get(i), program = programsByCode.get(code('PROG', i % 12))
    if (!student || !program) throw new DemoError('Seeded student or program inventory is incomplete.')
    const profile = await c.request('ACADEMIC_AFFAIRS', 'GET', base + `students/${student.id}/profile`)
    if (profile.programId !== program.id || profile.cohort !== cohortOf(i)) {
      throw new DemoError('Student academic profile does not match the seeded cohort and program.')
    }
    cohorts[profile.cohort] = (cohorts[profile.cohort] ?? 0) + 1
  }
  const curriculumIds = new Set(Object.entries(c.state.operations)
    .filter(([key, op]) => key.startsWith('curriculum:') && op.done).map(([, op]) => op.id))
  const curriculumRows = []
  for (const program of records.programs) curriculumRows.push(...await c.list('ACADEMIC_AFFAIRS', `${base}academic/curriculum?programId=${program.id}`, true))
  if (curriculumIds.size !== 84 || curriculumRows.length !== 84 || new Set(curriculumRows.map(row => row.id)).size !== 84
    || curriculumRows.some(row => !curriculumIds.has(row.id) || row.semesterIndex < 1 || row.semesterIndex > 7)) {
    throw new DemoError('Curriculum inventory does not match the approved 84 entries across 12 programs.')
  }
  records.curriculum = curriculumRows
  const weights = { GENERAL: [0.1, 0.4, 0.5, 0], MAJOR_PROJECT: [0.1, 0.4, 0, 0.5], MAJOR_FINAL: [0.1, 0.3, 0.6, 0] }
  const transcriptOf = email => c.request(email, 'GET', '/api/v1/me/transcript')
  const first = await transcriptOf('student.001@example.test')
  if (!first.programId || first.terms.length !== 2 || first.records.length !== 4) throw new DemoError('Expected demo transcript shape is missing.')
  for (const record of first.records) {
    const expected = weights[record.scheme], actual = [record.attendanceWeight, record.midtermWeight, record.finalWeight, record.projectWeight]
    if (!expected || Math.abs(actual.reduce((sum, value) => sum + value, 0) - 1) > 1e-9
      || actual.some((value, index) => Math.abs(value - expected[index]) > 1e-9)) {
      throw new DemoError('Transcript grade weights do not match the recorded scheme.')
    }
  }
  const best = new Map()
  for (const record of first.records) if (!best.has(record.course.id) || record.gradePoint > best.get(record.course.id).gradePoint) best.set(record.course.id, record)
  const totalCredits = [...best.values()].reduce((sum, record) => sum + record.course.credits, 0)
  const totalPoints = [...best.values()].reduce((sum, record) => sum + record.gradePoint * record.course.credits, 0)
  if (first.summary.cumulativeCredits !== totalCredits || first.summary.cumulativeGpa !== round2(totalPoints / totalCredits)) {
    throw new DemoError('Cumulative GPA does not reconcile with the best attempt per course.')
  }
  for (const term of first.terms) {
    const attempts = first.records.filter(record => record.term.id === term.termId), credits = attempts.reduce((sum, record) => sum + record.course.credits, 0)
    if (term.credits !== credits || term.gradedCourses !== attempts.length
      || term.gpa !== round2(attempts.reduce((sum, record) => sum + record.gradePoint * record.course.credits, 0) / credits)) {
      throw new DemoError('Semester GPA does not reconcile with retained attempts.')
    }
  }
  const retakeTranscript = await transcriptOf('student.002@example.test')
  const retaken = retakeTranscript.records.filter(record => record.course.code === 'CD-COURSE-008')
    .sort((left, right) => left.term.startDate.localeCompare(right.term.startDate))
  if (retaken.length !== 2 || retaken[0].passed || !retaken[1].passed || !retaken[1].retake || retaken[1].improvement) {
    throw new DemoError('Failed-course retake does not preserve both attempts with correct flags.')
  }
  const improvementTranscript = await transcriptOf('student.005@example.test')
  const improved = improvementTranscript.records.filter(record => record.course.code === 'CD-COURSE-002')
    .sort((left, right) => left.term.startDate.localeCompare(right.term.startDate))
  if (improved.length !== 2 || !improved[0].passed || !improved[1].passed || !improved[1].retake || !improved[1].improvement) {
    throw new DemoError('Improvement retake does not preserve both passed attempts.')
  }
  const firstCurriculum = await c.request('student.001@example.test', 'GET', '/api/v1/me/curriculum')
  const curriculumCourses = firstCurriculum.semesters.flatMap(semester => semester.courses)
  const foundation = curriculumCourses.find(course => course.code === 'CD-COURSE-061')
  const chained = curriculumCourses.find(course => course.code === 'CD-COURSE-062')
  const firstMajor = curriculumCourses.find(course => course.code === 'CD-COURSE-001')
  if (firstCurriculum.semesters.length !== 7 || curriculumCourses.length !== 7 || !foundation || foundation.status !== 'PASSED'
    || !chained || !chained.prerequisiteSatisfied || !firstMajor || firstMajor.prerequisiteSatisfied) {
    throw new DemoError('Curriculum progress or prerequisite satisfaction is incorrect for the sample student.')
  }
  const retakeCurriculum = await c.request('student.002@example.test', 'GET', '/api/v1/me/curriculum')
  const retakenCourse = retakeCurriculum.semesters.flatMap(semester => semester.courses).find(course => course.code === 'CD-COURSE-008')
  if (!retakenCourse || !retakenCourse.improvementAttempted || retakenCourse.status !== 'PASSED') {
    throw new DemoError('Curriculum best-attempt status does not reflect the retake.')
  }
  report.academicRecords = {
    profiles: records.students.length, cohorts, curriculumEntries: curriculumRows.length, grades: records.grades.length,
    retakes: Object.entries(c.state.operations).filter(([key, op]) => /^enrollment:retake:\d+$/.test(key) && op.done).length,
  }
  for (const event of records.events) {
    if (records.memberships.filter(m => m.eventId === event.id && m.status !== 'CANCELLED').length > event.capacity) throw new DemoError('Event capacity exceeded.')
  }
  for (const charge of records.charges) {
    const balance = await c.request('FINANCE', 'GET', base + `finance/charges/${charge.id}/balance`)
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
    ['ACADEMIC_AFFAIRS', 'PEOPLE', ['units', 'students', 'personnel']],
    ['ACADEMIC_AFFAIRS', 'ACADEMIC', ['programs', 'courses', 'terms', 'offerings', 'sections', 'enrollments', 'grades', 'curriculum']],
    ['CAMPUS_SERVICES', 'DORMITORY', ['buildings', 'rooms', 'beds', 'assignments']],
    ['CAMPUS_SERVICES', 'NOTIFICATION', ['templates', 'notices']],
    ['CAMPUS_SERVICES', 'EVENT', ['events', 'memberships']],
    ['CAMPUS_SERVICES', 'LIBRARY', ['titles', 'copies', 'loans']],
    ['FINANCE', 'FINANCE', ['fees', 'charges', 'payments']],
  ]) {
    const actor = (await c.session(role)).user.id
    const entries = await c.list('AUDITOR', `${base}audits/${source}?actorId=${actor}`, true)
    const covered = new Set(entries.filter(entry => entry.actorId === actor).map(entry => entry.targetId))
    const targets = names.flatMap(name => records[name].map(row => row.id))
    if (targets.some(id => !covered.has(id))) throw new DemoError(`Owner audit coverage is incomplete for ${role}.`)
    report.auditCoverage[`${role}:${source}`] = { fixtureTargets: targets.length, retainedEntries: entries.length }
  }
  const dashboard = await c.request('AUDITOR', 'GET', base + 'reports/dashboard')
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
