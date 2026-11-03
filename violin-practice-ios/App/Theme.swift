import SwiftUI

enum LKColor {
    static let ink = Color(hex: 0x17324D)
    static let muted = Color(hex: 0x4F687E)
    static let cream = Color(hex: 0xEDF9FF)
    static let paper = Color.white
    static let deepSky = Color(hex: 0x1E67A2)
    static let skyBlue = Color(hex: 0x3679AB)
    static let skyTop = Color(hex: 0xBDEBFF)
    static let skyBottom = Color(hex: 0xF1FAFF)
    static let cloudBlue = Color(hex: 0xDDF3FF)
    static let sunYellow = Color(hex: 0xFFD166)
    static let mintCloud = Color(hex: 0xDFF7EE)
    static let lavenderCloud = Color(hex: 0xEDE8FF)
    static let danger = Color(hex: 0xC04654)
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

struct SkyBackground: View {
    var body: some View {
        LinearGradient(
            colors: [LKColor.skyTop, LKColor.skyBottom],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

struct LKLogo: View {
    var size: CGFloat = 88

    var body: some View {
        Image("LKPracticeLogo")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel("LK Practice")
    }
}

struct CloudCardModifier: ViewModifier {
    var color: Color = Color.white.opacity(0.92)
    var radius: CGFloat = 26

    func body(content: Content) -> some View {
        content
            .background(color, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .stroke(LKColor.deepSky.opacity(0.10), lineWidth: 1)
            }
            .shadow(color: LKColor.deepSky.opacity(0.10), radius: 9, y: 4)
    }
}

extension View {
    func cloudCard(
        color: Color = Color.white.opacity(0.92),
        radius: CGFloat = 26
    ) -> some View {
        modifier(CloudCardModifier(color: color, radius: radius))
    }
}

struct SunnyButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.bold))
            .foregroundStyle(LKColor.ink)
            .padding(.horizontal, 18)
            .frame(minHeight: 50)
            .background(
                configuration.isPressed
                    ? LKColor.sunYellow.opacity(0.72)
                    : LKColor.sunYellow,
                in: RoundedRectangle(cornerRadius: 17, style: .continuous)
            )
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct SkyButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .frame(minHeight: 50)
            .background(
                configuration.isPressed
                    ? LKColor.deepSky.opacity(0.72)
                    : LKColor.deepSky,
                in: RoundedRectangle(cornerRadius: 17, style: .continuous)
            )
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

extension Font {
    static func lkTitle(_ size: CGFloat) -> Font {
        .system(size: size, weight: .heavy, design: .rounded)
    }
}
