import Foundation

/// Screenshot mode. Launch a Debug build with `-DeweyDemo` and every Schoology
/// op is answered from the made-up student below: no sign-in, no network. The
/// engine page is a blank page on the Schoology origin, so grade math still
/// runs in engine.js. Dates are relative to today, so "Due today" and "2 d
/// late" always read right. Saved copies go to their own folder and the item
/// store is in memory, but use a fresh simulator anyway (UserDefaults are shared).
enum Demo {
    #if DEBUG
    static let on = ProcessInfo.processInfo.arguments.contains("-DeweyDemo")
    #else
    static let on = false
    #endif

    /// Screenshots of other screens without tapping: `-DeweyTab 3` opens Classes,
    /// `-DeweyDay 1` selects tomorrow in the week strip (-1 = Late), `-DeweyAssignment 9206`
    /// opens that assignment.
    static var startTab: Int { on ? UserDefaults.standard.integer(forKey: "DeweyTab") : 0 }
    static var startAssignment: String? { on ? UserDefaults.standard.string(forKey: "DeweyAssignment") : nil }
    static var startDay: DayFilter? {
        guard on, UserDefaults.standard.object(forKey: "DeweyDay") != nil else { return nil }
        let k = UserDefaults.standard.integer(forKey: "DeweyDay")
        return k < 0 ? .late : .day(k)
    }

    /// Ops that only do math in engine.js; everything else is answered here.
    static let pureOps: Set<String> = ["courseGrade", "planTest", "annotate", "math"]

    /// The op's `data`, or nil to let engine.js run it (pure ops only).
    static func answer(_ op: String, _ args: [String: Any]) throws -> Any? {
        if pureOps.contains(op) { return nil }
        let id = args["id"] as? String ?? ""
        let sid = args["sectionId"] as? String ?? ""
        switch op {
        case "courses": return courses.map { ["sectionId": $0.id, "title": $0.title, "sectionTitle": $0.section] }
        case "todo": return ["overdue": events.filter { $0.day < 0 }.map(\.json), "upcoming": events.filter { $0.day >= 0 }.map(\.json)]
        case "status": return ["state": events.first { $0.id == id }?.status ?? "no_dropbox"]
        case "gradebook":
            guard let c = courses.first(where: { $0.id == sid }) else { throw EngineError.http(404) }
            return gradebook(c)
        case "assignment": return assignment(id)
        case "dropbox": return ["grade": NSNull(), "comments": [] as [Any]]
        case "materials": return materials[sid]?[args["folderId"] as? String ?? "root"] ?? []
        case "material": return material
        case "messages": return (args["page"] as? Int ?? 0) == 0 ? ["threads": threads, "unreadCount": 2, "hasMore": false] : ["threads": [] as [Any], "hasMore": false]
        case "message": return message(id)
        case "notifications": return (args["page"] as? Int ?? 0) == 0 ? ["items": notices, "hasMore": false] : ["items": [] as [Any], "hasMore": false]
        default: throw EngineError.http(404)
        }
    }

    /// The student's own marks and a few just-posted scores, before the first refresh.
    @MainActor
    static func seed(_ model: AppModel) {
        for e in events where e.mark != nil {
            let it = ItemState(schoologyId: e.id, title: e.title)
            it.sectionId = e.course.id
            it.courseName = e.course.title
            switch e.mark {
            case "done": it.done = true
            case "turnedIn": it.done = true; it.turnedIn = true
            case "studied": it.studied = true
            default: break
            }
            model.context.insert(it)
        }
        try? model.context.save()
        let now = Date()
        for (id, earned, hoursAgo) in [("9205", 47.0, 5.0), ("9131", 18.0, 26.0), ("9604", 19.0, 50.0)] {
            model.gradeLog[id] = .init(earned: earned, at: now.addingTimeInterval(-hoursAgo * 3600), seen: false)
        }
        Cache.save("gradeLog", model.gradeLog)
        let bio = courses[1].id
        RecentMaterials.add(bio, MaterialRow(kind: "document", title: "Syllabus 2026-27.pdf", id: "d-Syllabus 2026-27.pdf", url: file("Syllabus 2026-27.pdf")))
        RecentMaterials.add(bio, MaterialRow(kind: "document", title: "Unit 3 Slides.pdf", id: "d-Unit 3 Slides.pdf", url: file("Unit 3 Slides.pdf")))
    }

    // ── The made-up student ──────────────────────────────────────────────

    struct DemoCourse { var id: String; var title: String; var section: String }

    static let courses = [
        DemoCourse(id: "7000001202", title: "Algebra 2 - 1202", section: "ChenL p1 T1"),
        DemoCourse(id: "7000001101", title: "AP Biology - 1101", section: "RiveraM p2 T1"),
        DemoCourse(id: "7000001303", title: "English 10 - 1303", section: "PatelS p3 T1"),
        DemoCourse(id: "7000001404", title: "Spanish 2 - 1404", section: "GarciaJ p4 T1"),
        DemoCourse(id: "7000001505", title: "Chemistry H - 1505", section: "NguyenK p5 T1"),
        DemoCourse(id: "7000001606", title: "US History - 1606", section: "BrooksA p7 T1"),
        DemoCourse(id: "7000009100", title: "Gr10 Hub", section: "Class of 2029"),
    ]
    static func course(_ short: String) -> DemoCourse { courses.first { $0.title.hasPrefix(short) }! }

    /// Today at `hour:minute`, `day` days away.
    static func at(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        let cal = Calendar.current
        let d = cal.date(byAdding: .day, value: day, to: cal.startOfDay(for: Date()))!
        return cal.date(bySettingHour: hour, minute: minute, second: 0, of: d)!
    }

    struct Event {
        var id: String
        var title: String
        var course: DemoCourse
        var day: Int
        var hour: Int
        var minute = 59
        var quiz = false
        var status = "no_dropbox"
        var mark: String?

        var json: [String: Any] {
            let url = quiz ? "/course/\(course.id)/assessments/\(id)" : "/assignment/\(id)"
            return ["schoologyId": id, "title": title, "course": course.title, "sectionId": course.id,
                    "due": Fmt.isoPlain.string(from: Demo.at(day, hour, minute)), "graded": false, "url": url]
        }
    }

    static let events: [Event] = [
        Event(id: "9402", title: "Vocabulario 2.3 Practice", course: course("Spanish"), day: -2, hour: 18, status: "not_submitted"),
        Event(id: "9112", title: "HW 4.3: Pg. 212 #1-35 odd", course: course("Algebra"), day: -1, hour: 23, mark: "done"),
        Event(id: "9206", title: "Lab 2.2 Cell Size and Diffusion", course: course("AP Bio"), day: 0, hour: 23, status: "not_submitted"),
        Event(id: "9207", title: "Read Ch. 5: Membranes", course: course("AP Bio"), day: 0, hour: 23, mark: "turnedIn"),
        Event(id: "9502", title: "Stoichiometry Worksheet", course: course("Chem"), day: 1, hour: 8, minute: 30),
        Event(id: "9503", title: "Unit 2 Quiz", course: course("Chem"), day: 1, hour: 9, minute: 40, mark: "studied"),
        Event(id: "9304", title: "Essay Draft: The Crucible", course: course("English"), day: 2, hour: 23, status: "submitted"),
        Event(id: "9208", title: "Quiz 3.1 Cellular Respiration", course: course("AP Bio"), day: 2, hour: 10, minute: 5, quiz: true),
        Event(id: "9133", title: "Unit 4 Test (Ch. 4 and 5)", course: course("Algebra"), day: 3, hour: 8, minute: 0),
        Event(id: "9603", title: "DBQ Outline: Reconstruction", course: course("US History"), day: 4, hour: 23),
        Event(id: "9404", title: "Cultura: Día de los Muertos slides", course: course("Spanish"), day: 6, hour: 23),
        Event(id: "9305", title: "Read Act 3", course: course("English"), day: 8, hour: 8, minute: 0),
    ]

    // ── Gradebooks ───────────────────────────────────────────────────────

    /// (id, title, day, earned, possible, comment); earned nil = not graded yet.
    typealias G = (String, String, Int, Double?, Double, String)

    static let books: [String: [(String, Double, [G])]] = [
        "Algebra 2": [
            ("Homework", 20, [("9101", "HW 1.1", -56, 10, 10, ""), ("9102", "HW 2.3", -42, 9, 10, ""), ("9103", "HW 3.2", -28, 10, 10, ""),
                              ("9104", "HW 4.1", -9, 8, 10, "Show your work on #14."), ("9112", "HW 4.3: Pg. 212 #1-35 odd", -1, nil, 10, "")]),
            ("Quizzes", 30, [("9121", "Quiz 1", -45, 17, 20, ""), ("9122", "Quiz 2", -30, 16, 20, ""), ("9131", "Quiz 4.2", -1, 18, 20, "")]),
            ("Tests", 50, [("9123", "Unit 1 Test", -38, 44, 50, ""), ("9124", "Unit 2 Test", -20, 45, 50, ""), ("9133", "Unit 4 Test (Ch. 4 and 5)", 3, nil, 50, "")]),
        ],
        "AP Biology": [
            ("Labs & Homework", 30, [("9201", "Lab Safety Contract", -58, 5, 5, ""), ("9210", "Lab 1.1 Measuring pH", -49, 10, 10, ""),
                                     ("9211", "Ch. 2 Reading Guide", -47, 10, 10, ""),
                                     ("9212", "Lab 1.2 Enzyme Activity", -40, 19, 20, "Great graph. Label your axis units next time."),
                                     ("9213", "Ch. 3 Reading Guide", -35, 10, 10, ""), ("9214", "Lab 2.1 Osmosis in Potatoes", -28, 20, 20, ""),
                                     ("9215", "Ch. 4 Reading Guide", -21, 9, 10, ""), ("9206", "Lab 2.2 Cell Size and Diffusion", 0, nil, 20, ""),
                                     ("9311", "Lab 3.1 Leaf Disk Photosynthesis", 7, nil, 20, "")]),
            ("Tests & Quizzes", 70, [("9202", "Unit 1 Quiz", -44, 18, 20, ""), ("9203", "Unit 1 Test", -33, 46.5, 50, ""),
                                     ("9204", "Quiz 2.1 Membranes", -26, 10, 10, ""), ("9205", "Unit 2 Test", -2, 47, 50, "Nice work on the FRQ."),
                                     ("9208", "Quiz 3.1 Cellular Respiration", 2, nil, 10, "")]),
        ],
        "English 10": [
            ("Writing", 40, [("9301", "Personal Narrative", -40, 46, 50, "Strong voice. Tighten the ending."), ("9302", "Literary Analysis Paragraph", -6, 18, 20, ""),
                             ("9304", "Essay Draft: The Crucible", 2, nil, 50, "")]),
            ("Reading", 30, [("9303", "Reading Check: Act 1", -12, 9, 10, ""), ("9306", "Socratic Seminar 1", -30, 10, 10, "")]),
            ("Participation", 30, [("9307", "Participation, weeks 1-6", -3, 30, 30, "")]),
        ],
        "Spanish 2": [
            ("Tareas", 20, [("9401", "Vocabulario 2.1", -30, 10, 10, ""), ("9402", "Vocabulario 2.3 Practice", -2, nil, 10, "")]),
            ("Pruebas", 40, [("9403", "Prueba 2.1", -25, 18, 20, ""), ("9405", "Prueba 2.2", -10, 17, 20, "")]),
            ("Exámenes", 40, [("9406", "Examen: Unidad 1", -20, 46, 50, "")]),
        ],
        "Chemistry H": [
            ("Labs", 25, [("9501", "Lab: Density", -40, 19, 20, ""), ("9504", "Lab: Flame Test", -20, 18, 20, "")]),
            ("Homework", 15, [("9505", "Ch. 1 Problems", -45, 10, 10, ""), ("9506", "Ch. 2 Problems", -30, 8, 10, ""),
                              ("9502", "Stoichiometry Worksheet", 1, nil, 10, "")]),
            ("Tests", 60, [("9507", "Unit 1 Test", -25, 42, 50, ""), ("9503", "Unit 2 Quiz", 1, nil, 25, "")]),
        ],
        "US History": [
            ("Assignments", 40, [("9601", "Primary Source Analysis 1", -35, 20, 20, ""), ("9602", "Chapter 3 Notes", -22, 10, 10, ""),
                                 ("9603", "DBQ Outline: Reconstruction", 4, nil, 20, "")]),
            ("Assessments", 60, [("9605", "Unit 1 Test", -18, 48, 50, ""), ("9604", "Quiz: Reconstruction Vocab", -4, 19, 20, "")]),
        ],
    ]

    static let wall: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "M/d/yy h:mma"
        return f
    }()

    /// parseGrades-shaped rows: course, period, then each category and its items.
    static func gradebook(_ c: DemoCourse) -> [[String: Any]] {
        guard let book = books[Course.short(c.title)] else { return [] }
        var rows: [[String: Any]] = [["level": "course", "title": "Course Grade", "due": "", "grade": "", "comment": ""],
                                     ["level": "period", "title": "26-27 T1", "due": "", "grade": "", "comment": ""]]
        for (cat, weight, items) in book {
            rows.append(["level": "category", "title": cat, "due": "", "grade": "", "comment": "", "weight": weight])
            for (id, title, day, earned, possible, comment) in items {
                let score = earned.map { "\(fmt($0)) / \(fmt(possible))" } ?? "—"
                rows.append(["level": "item", "title": title, "due": wall.string(from: at(day, 23, 59)), "grade": score, "comment": comment, "id": id])
            }
        }
        return rows
    }

    static func fmt(_ x: Double) -> String { x == x.rounded() ? String(Int(x)) : String(x) }

    // ── Assignments, materials, inbox ────────────────────────────────────

    static func assignment(_ id: String) -> [String: Any] {
        let e = events.first { $0.id == id }
        let title = e?.title ?? books.values.flatMap { $0 }.flatMap(\.2).first { $0.0 == id }?.1 ?? "Assignment"
        var a: [String: Any] = [
            "id": id, "title": title, "type": e?.quiz == true ? "quiz" : "assignment", "attachments": [["url": file("\(title) handout.pdf"), "title": "\(title) handout.pdf"]],
            "instructionsMd": "Work through the handout, then hand in **one PDF** with your answers.\n\nQuestions? Come to tutorial on Wednesday.",
        ]
        if let e { a["due"] = Fmt.isoPlain.string(from: at(e.day, e.hour, e.minute)) }
        if let s = e?.status, s != "no_dropbox" { a["submission"] = ["state": s] }
        return a
    }

    static func file(_ name: String) -> String {
        "\(Engine.origin.absoluteString)/system/files/demo/\(name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name)"
    }

    static func folder(_ id: String, _ title: String) -> [String: Any] { ["kind": "folder", "title": title, "id": id] }
    static func doc(_ title: String) -> [String: Any] { ["kind": "document", "title": title, "id": "d-\(title)", "url": file(title)] }
    static func work(_ id: String, _ title: String) -> [String: Any] { ["kind": "assignment", "title": title, "id": id, "url": "/assignment/\(id)"] }

    static var materials: [String: [String: [[String: Any]]]] {
        let bio = courses[1].id
        var m: [String: [String: [[String: Any]]]] = [
            bio: [
                "root": [folder("info", "Course Information"), folder("u1", "Unit 1 - Chemistry of Life"), folder("u2", "Unit 2 - Cells and Membranes"),
                         folder("u3", "Unit 3 - Cell Energy"), doc("Syllabus 2026-27.pdf")],
                "info": [doc("Syllabus 2026-27.pdf"), work("9201", "Lab Safety Contract")],
                "u1": [doc("Unit 1 Slides.pdf"), work("9210", "Lab 1.1 Measuring pH"), work("9211", "Ch. 2 Reading Guide"), work("9212", "Lab 1.2 Enzyme Activity")],
                "u2": [doc("Unit 2 Slides.pdf"), work("9214", "Lab 2.1 Osmosis in Potatoes"), work("9206", "Lab 2.2 Cell Size and Diffusion"),
                       work("9207", "Read Ch. 5: Membranes")],
                "u3": [doc("Unit 3 Slides.pdf"), work("9311", "Lab 3.1 Leaf Disk Photosynthesis"),
                       ["kind": "page", "title": "Photosynthesis reading", "id": "55501", "url": "/page/55501"],
                       ["kind": "document", "title": "Lab 3.1 guided notes", "id": "d-gn", "url": "https://docs.google.com/document/d/demo/edit"],
                       ["kind": "quiz", "title": "Quiz 3.1 Cellular Respiration", "id": "9208", "url": "/course/\(bio)/assessments/9208"]],
            ],
        ]
        for c in courses where c.id != bio && !c.title.hasPrefix("Gr10") {
            m[c.id] = ["root": [folder("a", "Course Information"), folder("b", "Unit 1"), folder("c", "Unit 2"), doc("Syllabus.pdf")]]
        }
        return m
    }

    static let material: [String: Any] = [
        "kind": "page", "title": "Photosynthesis reading", "files": [] as [Any], "links": [] as [Any],
        "bodyMd": "Read pages 198-211 before Thursday's lab.\n\n- What do the light reactions make?\n- Where does the Calvin cycle happen?",
    ]

    static func ago(hours: Double) -> String { Fmt.isoPlain.string(from: Date().addingTimeInterval(-hours * 3600)) }

    static var threads: [[String: Any]] {
        let bio = courses[1], alg = courses[0]
        return [
            ["id": "m1", "subject": "Bring your data tables tomorrow", "group": "\(bio.title): \(bio.section)", "from": "Maria Rivera",
             "preview": "We'll graph the diffusion results in class, so have your tables filled in.", "date": ago(hours: 3), "unread": true],
            ["id": "m2", "subject": "Unit 4 Test review packet", "group": "\(alg.title): \(alg.section)", "from": "Lin Chen",
             "preview": "The review packet is posted under Unit 4. Answers go up Friday.", "date": ago(hours: 28), "unread": false],
            ["id": "m3", "subject": "Picture day is Thursday", "group": "Gr10 Hub: Class of 2029", "from": "Front Office",
             "preview": "Retakes are in the library from 8:00 to 11:30.", "date": ago(hours: 46), "unread": true],
        ]
    }

    static func message(_ id: String) -> [String: Any] {
        let t = threads.first { $0["id"] as? String == id } ?? threads[0]
        return ["id": id, "subject": t["subject"]!, "group": t["group"]!, "participants": [t["from"]!, "You"],
                "messages": [["author": t["from"]!, "date": t["date"]!, "bodyMd": t["preview"]!, "attachments": [] as [Any]]]]
    }

    static var notices: [[String: Any]] {
        let bio = courses[1], us = courses[5]
        return [
            ["key": "n1", "type": "material", "course": "\(bio.title): \(bio.section)", "sectionId": bio.id, "text": "Maria Rivera posted 2 new items",
             "date": ago(hours: 6), "items": [["title": "Unit 3 Slides.pdf", "url": "/course/\(bio.id)/materials", "kind": "material"],
                                              ["title": "Lab 3.1 Leaf Disk Photosynthesis", "url": "/assignment/9311", "kind": "assignment"]]],
            ["key": "n2", "type": "material", "course": "\(us.title): \(us.section)", "sectionId": us.id, "text": "Alex Brooks posted DBQ Outline: Reconstruction",
             "date": ago(hours: 30), "items": [["title": "DBQ Outline: Reconstruction", "url": "/assignment/9603", "kind": "assignment"]]],
        ]
    }
}
