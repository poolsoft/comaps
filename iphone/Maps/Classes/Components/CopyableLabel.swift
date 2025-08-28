// Label shows copy popup menu on tap or long tap.
class CopyableLabel: UILabel {

  override init(frame: CGRect) {
    super.init(frame: frame)
    self.sharedInit()
  }

  required init?(coder aDecoder: NSCoder) {
    super.init(coder: aDecoder)
    self.sharedInit()
  }

  private func sharedInit() {
    self.isUserInteractionEnabled = true
    self.gestureRecognizers = [
        UILongPressGestureRecognizer(target: self, action: #selector(self.showMenu)),
        UITapGestureRecognizer(target: self, action: #selector(self.showMenu))
    ]
  }

  @objc func showMenu(_ recognizer: UILongPressGestureRecognizer) {
    self.becomeFirstResponder()

    let menu = UIMenuController.shared
    let locationOfTouchInLabel = recognizer.location(in: self)

    if !menu.isMenuVisible {
      var rect = bounds
      rect.origin = locationOfTouchInLabel
      rect.size = CGSize(width: 1, height: 1)
      menu.showMenu(from: self, rect: rect)
    }
  }

  override func copy(_ sender: Any?) {
    UIPasteboard.general.string = text
    UIMenuController.shared.hideMenu()
  }

  override var canBecomeFirstResponder: Bool {
    return true
  }

  override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
    return action == #selector(UIResponderStandardEditActions.copy)
  }
}
