import XCTest

/// R5 · Apple's accessibility audit (VoiceOver labels, hit targets, clipped
/// Dynamic Type text, contrast) on each main screen, in light and dark at the
/// largest text size. Runs against the simulator's signed-in session; every
/// issue is printed with "A11Y:" so one run lists them all.
/// `xcodebuild -scheme A11yAudit -collect-test-diagnostics never test` (without the flag a failing run
/// spends up to 10 minutes in `simctl diagnose` after the tests finish, and looks stuck).
final class AccessibilityAudit: XCTestCase {
    override func setUp() { continueAfterFailure = true }

    func testLight() throws { try audit(appearance: "light") }
    func testDark() throws { try audit(appearance: "dark") }

    private func audit(appearance: String) throws {
        let app = XCUIApplication()
        app.launchArguments += ["-appearance", appearance,
                                "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 20), "signed in, tab bar showing")
        var issues: [String] = []

        // Contrast is left to ThemeTests (every colour pair, computed): the audit samples
        // pixels and flags text under the translucent tab bar and toolbars.
        let types = XCUIAccessibilityAuditType.all.subtracting(.contrast)
        func check(_ screen: String) throws {
            sleep(3)
            try app.performAccessibilityAudit(for: types) { issue in
                // System chrome (the sheet's Done button, the tab bar) isn't the app's to fix.
                if let e = issue.element, e.elementType == .button, app.navigationBars.buttons[e.label].exists { return true }
                let line = "A11Y: [\(appearance)] \(screen) · \(issue.auditType) · \(issue.compactDescription) · \(issue.element?.label ?? "-") (\(issue.element?.elementType.rawValue ?? 0))"
                print(line)
                issues.append(line)
                return true   // collected; reported together below
            }
        }

        /// Opens the first button whose VoiceOver label contains `text`, audits it, goes back.
        func open(_ text: String, as screen: String) throws {
            let b = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
            guard b.waitForExistence(timeout: 5) else { return XCTFail("no \(screen) to open") }
            b.tap()
            try check(screen)
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }

        // Read-only screens only: nothing here marks, submits or opens a message.
        for tab in ["To Do", "Grades", "Inbox", "Classes"] {
            app.tabBars.buttons[tab].tap()
            try check(tab)
            if tab == "Grades" { try open("A line", as: "Class grades") }
            if tab == "Classes" { try open("grade ", as: "Class") }
        }
        app.tabBars.buttons["To Do"].tap()
        let gear = app.buttons["Settings"]
        if gear.waitForExistence(timeout: 5) {
            gear.tap()
            try check("Settings")
        }
        XCTAssertTrue(issues.isEmpty, "\(issues.count) accessibility issues:\n" + issues.joined(separator: "\n"))
    }
}
