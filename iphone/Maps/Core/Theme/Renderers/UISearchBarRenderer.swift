import Foundation
extension UISearchBar {
  @objc override func applyTheme() {
    if #available(iOS 26, *) {
      return;
    }
      
    if styleName.isEmpty {
      setStyle(.searchBar)
    }
    for style in StyleManager.shared.getStyle(styleName)
      where !style.isEmpty && !style.hasExclusion(view: self) {
      UISearchBarRenderer.render(self, style: style)
    }
  }

  @objc override func sw_didMoveToWindow() {
    guard MapsAppDelegate.theApp().window === window else {
      sw_didMoveToWindow();
      return
    }
    applyTheme()
    isStyleApplied = true
    sw_didMoveToWindow();
  }
}

class UISearchBarRenderer: UIViewRenderer {
  class func render(_ control: UISearchBar, style: Style) {
    super.render(control, style: style)

    if #available(iOS 26, *) {
      return;
    }
      
    let searchTextField = control.searchTextField
    // Default search bar implementation adds the grey transparent image for background. This code removes it and updates the corner radius. This is not working on iPad designed for mac.
    if !ProcessInfo.processInfo.isiOSAppOnMac {
      control.setSearchFieldBackgroundImage(UIImage(), for: .normal)
    }
    searchTextField.layer.setCornerRadius(.buttonDefault)
    searchTextField.layer.masksToBounds = true
    // Placeholder color
    if let placeholder = searchTextField.placeholder {
      searchTextField.attributedPlaceholder = NSAttributedString(string: placeholder, attributes: [.foregroundColor: UIColor.gray])
    }
    if let backgroundColor = style.backgroundColor {
      searchTextField.backgroundColor = backgroundColor
    }
    if let font = style.font {
      searchTextField.font = font
    }
    if let fontColor = style.fontColor {
      searchTextField.textColor = fontColor
    }
    if let tintColor = style.tintColor {
      searchTextField.leftView?.tintColor = tintColor
      // Placeholder indicator color
      searchTextField.tintColor = tintColor
      // Clear button image
      let clearButtonImage = UIImage(named: "ic_clear")?.withRenderingMode(.alwaysTemplate).withTintColor(tintColor)
      control.setImage(clearButtonImage, for: .clear, state: .normal)
    }
    if let barTintColor = style.barTintColor {
      let position = control.delegate?.position?(for: control) ?? control.barPosition
      control.setBackgroundImage(barTintColor.getImage(), for: position, barMetrics: .defaultPrompt)
      control.setBackgroundImage(barTintColor.getImage(), for: position, barMetrics: .default)
      control.backgroundColor = barTintColor
    }
    if let fontColorDetailed = style.fontColorDetailed {
      // Cancel button color
      control.tintColor = fontColorDetailed
    }
  }

  @available(iOS, deprecated: 13.0)
  private static let kiOS12DefaultSystemTextFieldHeight = 36

  @available(iOS, deprecated: 13.0)
  private static var searchBarBackgroundColor: UIColor?

  @available(iOS, deprecated: 13.0)
  private static var searchBarBackgroundImage: UIImage?
}
