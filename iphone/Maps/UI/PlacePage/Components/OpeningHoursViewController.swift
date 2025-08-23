import UIKit

class OpeningHoursTodayViewController: UIViewController {
  @IBOutlet var todayLabel: UILabel!
  @IBOutlet var scheduleLabel: UILabel!
  @IBOutlet var breaksLabel: UILabel!
  @IBOutlet var closedLabel: UILabel!
  @IBOutlet var arrowImageView: UIImageView!

  var workingDay: WorkingDay!
  var closedNow = false
  var onExpand: MWMVoidBlock?

  override func viewDidLoad() {
    super.viewDidLoad()

    todayLabel.text = workingDay.workingDays
    scheduleLabel.text = workingDay.workingTimes
    breaksLabel.text = workingDay.breaks
    closedLabel.isHidden = !closedNow
  }

  @IBAction func onTap(_ sender: UITapGestureRecognizer) {
    onExpand?()
  }
}

class OpeningHoursDayViewController: UIViewController {
  @IBOutlet var todayLabel: UILabel!
  @IBOutlet var scheduleLabel: UILabel!
  @IBOutlet var breaksLabel: UILabel!

  var workingDay: WorkingDay!

  override func viewDidLoad() {
    super.viewDidLoad()

    todayLabel.text = workingDay.workingDays
    scheduleLabel.text = workingDay.workingTimes
    breaksLabel.text = workingDay.breaks
  }
}

class OpeningHoursViewController: UIViewController {
  @IBOutlet var stackView: UIStackView!
  @IBOutlet var checkDateLabel: UILabel!
  @IBOutlet var checkDateLabelTopLayoutConstraint: NSLayoutConstraint!
  @IBOutlet var checkDateLabelBottomLayoutConstraint: NSLayoutConstraint!

  private var otherDaysViews: [OpeningHoursDayViewController] = []

  private lazy var todayView: OpeningHoursTodayViewController = {
    let vc = storyboard!.instantiateViewController(ofType: OpeningHoursTodayViewController.self)
    vc.workingDay = openingHours.days[0]
    vc.closedNow = openingHours.isClosedNow
    return vc
  }()

  private var expanded = false

  var openingHours: OpeningHours!
  var openingHoursCheckDate: Date?

  override func viewDidLoad() {
    super.viewDidLoad()

    addToStack(todayView)

    if openingHours.days.count == 1 {
      todayView.arrowImageView.isHidden = true
      return
    }
    
    openingHours.days.suffix(from: 1).forEach {
      let vc = createDayItem($0)
      otherDaysViews.append(vc)
      addToStack(vc)
    }

    todayView.onExpand = { [unowned self] in
      self.expanded = !self.expanded
      UIView.animate(withDuration: kDefaultAnimationDuration) {
        self.otherDaysViews.forEach { vc in
          vc.view.isHidden = !self.expanded
        }

        if let checkDate = self.openingHoursCheckDate, self.expanded {
          let checkDateFormatter = RelativeDateTimeFormatter()
          checkDateFormatter.unitsStyle = .spellOut
          checkDateFormatter.localizedString(for: checkDate, relativeTo: Date.now)
          self.checkDateLabel.text = String(format: L("hours_confirmed_time_ago"),  checkDateFormatter.localizedString(for: checkDate, relativeTo: Date.now))

          NSLayoutConstraint.activate([self.checkDateLabelTopLayoutConstraint])
          NSLayoutConstraint.activate([self.checkDateLabelBottomLayoutConstraint])
        } else {
          self.checkDateLabel.text = String()

          NSLayoutConstraint.deactivate([self.checkDateLabelTopLayoutConstraint])
          NSLayoutConstraint.deactivate([self.checkDateLabelBottomLayoutConstraint])
        }
        self.checkDateLabel.isHidden = !self.expanded

        self.todayView.arrowImageView.transform = self.expanded ? CGAffineTransform(rotationAngle: -CGFloat.pi + 0.01)
                                                                : CGAffineTransform.identity
        self.view.layoutIfNeeded()
      }
    }
  }

  private func createDayItem(_ workingDay: WorkingDay) -> OpeningHoursDayViewController {
    let vc = storyboard!.instantiateViewController(ofType: OpeningHoursDayViewController.self)
    vc.workingDay = workingDay
    vc.view.isHidden = true
    return vc
  }
    
  private func addToStack(_ viewController: UIViewController) {
    addChild(viewController)
    stackView.addArrangedSubview(viewController.view)
    viewController.didMove(toParent: self)
  }
}
