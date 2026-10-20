import Foundation

/// Code 39, the barcode on an FUHSD student ID card (docs/ios/FINDINGS.md):
/// each character is 5 bars and 4 spaces, 3 of the 9 wide, framed by `*`.
/// Digits only here, since a student number is digits. Pure, so tests can
/// decode what it draws.
enum Code39 {
    /// Wide (1) or narrow (0) for bar, space, bar, … of each character.
    private static let table: [Character: String] = [
        "0": "000110100", "1": "100100001", "2": "001100001", "3": "101100000", "4": "000110001",
        "5": "100110000", "6": "001110000", "7": "000100101", "8": "100100100", "9": "001100100",
        "*": "010010100",
    ]
    /// Wide to narrow (Code 39 allows 2…3; 3 reads best off a phone screen).
    static let ratio = 3
    /// Blank narrow modules on each side, so a scanner finds the edges.
    static let quiet = 10

    static func canEncode(_ s: String) -> Bool { !s.isEmpty && s.allSatisfy { $0.isASCII && $0.isNumber } }

    /// The barcode as modules, true = bar, left to right, quiet zones included;
    /// nil if `s` isn't all digits.
    static func modules(_ s: String) -> [Bool]? {
        guard canEncode(s) else { return nil }
        var out = Array(repeating: false, count: quiet)
        for (i, ch) in ("*" + s + "*").enumerated() {
            if i > 0 { out.append(false) }  // the narrow gap between characters
            for (j, w) in table[ch]!.enumerated() {
                out += Array(repeating: j % 2 == 0, count: w == "1" ? ratio : 1)
            }
        }
        return out + Array(repeating: false, count: quiet)
    }
}
