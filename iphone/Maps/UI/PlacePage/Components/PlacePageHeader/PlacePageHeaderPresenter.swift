protocol PlacePageHeaderPresenterProtocol: AnyObject {
  var objectType: PlacePageObjectType { get }
  
  func configure()
  func onClosePress()
  func onExpandPress()
  func onShareButtonPress(from sourceView: UIView)
  func onExportTrackButtonPress(_ type: KmlFileType, from sourceView: UIView)
}

protocol PlacePageHeaderViewControllerDelegate: AnyObject {
  func previewDidPressClose()
  func previewDidPressExpand()
  func previewDidPressShare(from sourceView: UIView)
  func previewDidPressExportTrack(_ type: KmlFileType, from sourceView: UIView)
}

class PlacePageHeaderPresenter {
  enum HeaderType {
    case flexible
    case fixed
  }

  private weak var view: PlacePageHeaderViewProtocol?
  private let placePagePreviewData: PlacePagePreviewData
  private let branch: String?
  let objectType: PlacePageObjectType
  private weak var delegate: PlacePageHeaderViewControllerDelegate?
  private let headerType: HeaderType

  init(view: PlacePageHeaderViewProtocol,
       placePagePreviewData: PlacePagePreviewData,
       branch: String?,
       objectType: PlacePageObjectType,
       delegate: PlacePageHeaderViewControllerDelegate?,
       headerType: HeaderType) {
    self.view = view
    self.delegate = delegate
    self.placePagePreviewData = placePagePreviewData
    self.branch = branch
    self.objectType = objectType
    self.headerType = headerType
  }
}

extension PlacePageHeaderPresenter: PlacePageHeaderPresenterProtocol {
  func configure() {
    view?.setTitle(placePagePreviewData.title, secondaryTitle: placePagePreviewData.secondaryTitle, branch: branch)
    switch headerType {
    case .flexible:
      view?.isExpandViewHidden = false
      view?.isShadowViewHidden = true
    case .fixed:
      view?.isExpandViewHidden = true
      view?.isShadowViewHidden = false
    }
  }

  func onClosePress() {
    delegate?.previewDidPressClose()
  }

  func onExpandPress() {
    delegate?.previewDidPressExpand()
  }

  func onShareButtonPress(from sourceView: UIView) {
    delegate?.previewDidPressShare(from: sourceView)
  }

  func onExportTrackButtonPress(_ type: KmlFileType, from sourceView: UIView) {
    delegate?.previewDidPressExportTrack(type, from: sourceView)
  }
}
