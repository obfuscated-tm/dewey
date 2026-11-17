import Foundation

/// Bell schedules of real schools, to start Settings → Schedule from. Typed from each
/// school's published weekly bell schedule; staff-only blocks (collaboration) are left
/// out. Add a school only from its own published schedule, never a guess.
struct BellTemplate: Identifiable {
    struct Day {
        var name: String
        var weekdays: [Int]
        /// (period name, "8:30", "9:15"), afternoon times written 12-hour as on the sheet.
        var slots: [(String, String, String)]
    }

    var id: String { school }
    var district: String
    var school: String
    var year: String
    var days: [Day]

    /// A whole schedule: one day type per Day, the weekdays set, nothing else.
    var schedule: BellSchedule {
        var s = BellSchedule()
        for d in days {
            var t = BellSchedule.DayType(name: d.name)
            t.slots = d.slots.map { .init(name: $0.0, start: Self.minutes($0.1), end: Self.minutes($0.2)) }
            s.dayTypes.append(t)
            for wd in d.weekdays { s.week[wd] = t.id }
        }
        return s
    }

    /// "1:30" on a school bell sheet is 1:30 pm: hours 1 to 6 are afternoon.
    static func minutes(_ clock: String) -> Int {
        let parts = clock.split(separator: ":").compactMap { Int($0) }
        let h = parts[0] < 7 ? parts[0] + 12 : parts[0]
        return h * 60 + parts[1]
    }

    /// The Fremont Union weekly schedule, 2026–27: the same class times at every school
    /// (chs, fhs, hhs, lhs, mvhs.fuhsd.org bell schedule pages, checked 2026-11-15). Monta
    /// Vista's sheet runs brunch and lunch up to the next bell, so it passes its own ends.
    private static func fuhsd(brunch: [String], lunch: [String]) -> [Day] {
        [
            Day(name: "Monday", weekdays: [2], slots: [
                ("P1", "8:30", "9:15"), ("P2", "9:20", "10:05"), ("Tutorial", "10:10", "10:35"), ("P3", "10:40", "11:25"),
                ("Brunch", "11:25", brunch[0]), ("P4", "11:50", "12:35"), ("P5", "12:40", "1:25"), ("Lunch", "1:25", lunch[0]),
                ("P6", "2:15", "3:00"), ("P7", "3:05", "3:50"),
            ]),
            Day(name: "Tuesday & Thursday", weekdays: [3, 5], slots: [
                ("P1", "8:30", "10:00"), ("P2", "10:05", "11:35"), ("Brunch", "11:35", brunch[1]), ("P3", "12:00", "1:30"),
                ("Lunch", "1:30", lunch[1]), ("P7", "2:20", "3:50"),
            ]),
            Day(name: "Wednesday & Friday", weekdays: [4, 6], slots: [
                ("P4", "8:30", "10:05"), ("Tutorial", "10:10", "10:50"), ("Brunch", "10:50", brunch[2]), ("P5", "11:15", "12:45"),
                ("Lunch", "12:45", lunch[2]), ("P6", "1:35", "3:05"),
            ]),
        ]
    }

    static let all: [BellTemplate] = {
        let common = fuhsd(brunch: ["11:40", "11:50", "11:05"], lunch: ["2:05", "2:10", "1:25"])
        let mv = fuhsd(brunch: ["11:45", "11:55", "11:10"], lunch: ["2:10", "2:15", "1:30"])
        return ["Cupertino High", "Fremont High", "Homestead High", "Lynbrook High"].map {
            BellTemplate(district: "Fremont Union", school: $0, year: "2026–27", days: common)
        } + [BellTemplate(district: "Fremont Union", school: "Monta Vista High", year: "2026–27", days: mv)]
    }()
}
