import CarPlay

final class ListTemplateBuilder {
  enum ListTemplateType {
    case history
    case bookmarkLists
    case bookmarks(category: BookmarkGroup)
    case searchResults(results: [MWMCarPlaySearchResultObject])
  }
  
  enum BarButtonType {
    case bookmarks
    case search
  }
  
  // MARK: - CPListTemplate builder
  class func buildListTemplate(for type: ListTemplateType) -> CPListTemplate {
    var title = ""
    var trailingNavigationBarButtons = [CPBarButton]()
    switch type {
    case .history:
      title = L("pick_destination")
      let bookmarksButton = buildBarButton(type: .bookmarks) { _ in
        let listTemplate = ListTemplateBuilder.buildListTemplate(for: .bookmarkLists)
        CarPlayService.shared.pushTemplate(listTemplate, animated: true)
      }
      let searchButton = buildBarButton(type: .search) { _ in
        if CarPlayService.shared.isKeyboardLimited {
          CarPlayService.shared.showKeyboardAlert()
        } else {
          let searchTemplate = SearchTemplateBuilder.buildSearchTemplate()
          CarPlayService.shared.pushTemplate(searchTemplate, animated: true)
        }
      }
      trailingNavigationBarButtons = [searchButton, bookmarksButton]
    case .bookmarkLists:
      title = L("bookmarks")
    case .searchResults:
      title = L("search_results")
    case .bookmarks(let category):
      title = category.title
    }

    let sections = buildSectionsForType(type)
    let template = CPListTemplate(title: title, sections: sections)
    template.trailingNavigationBarButtons = trailingNavigationBarButtons
    return template
  }

  private class func buildSectionsForType(_ type: ListTemplateType) -> [CPListSection] {
    switch type {
    case .history:
      return buildHistorySections()
    case .bookmarks(let category):
      return buildBookmarksSections(categoryId: category.categoryId)
    case .bookmarkLists:
      return buildBookmarkListsSections()
    case .searchResults(let results):
      return buildSearchResultsSections(results)
    }
  }

  private class func buildHistorySections() -> [CPListSection] {
    let searchQueries = FrameworkHelper.obtainLastSearchQueries()
    let items = searchQueries.map({ (text) -> CPListItem in
      let item = CPListItem(text: text, detailText: nil, image: UIImage(named: "recent"))
      item.userInfo = ListItemInfo(type: CPConstants.ListItemType.history,
                                   metadata: nil)
      return item
    })
    return [CPListSection(items: items)]
  }

  private class func buildBookmarkListsSections() -> [CPListSection] {
    let bookmarkManager = BookmarksManager.shared()
    let categories = bookmarkManager.sortedUserCategories()
    let items: [CPListItem] = categories.compactMap({ category in
      if category.bookmarksCount == 0 { return nil }
      let placesString = category.placesCountTitle()
      let item = CPListItem(text: category.title, detailText: placesString)
      item.userInfo = ListItemInfo(type: CPConstants.ListItemType.bookmarkLists,
                                   metadata: CategoryInfo(category: category))
      return item
    })
    return [CPListSection(items: items)]
  }

  private class func buildBookmarksSections(categoryId: MWMMarkGroupID) -> [CPListSection] {
    let bookmarkManager = BookmarksManager.shared()
    let bookmarks = bookmarkManager.bookmarks(forCategory: categoryId)
    var items = bookmarks.map({ (bookmark) -> CPListItem in
      let item = CPListItem(text: bookmark.prefferedName, detailText: bookmark.address)
      item.userInfo = ListItemInfo(type: CPConstants.ListItemType.bookmarks,
                                   metadata: BookmarkInfo(categoryId: categoryId,
                                                          bookmarkId: bookmark.bookmarkId))
      return item
    })
    let maxItemCount = CPListTemplate.maximumItemCount - 1
    if items.count >= maxItemCount {
      items = Array(items.prefix(maxItemCount))
      let cropWarning = CPListItem(text: L("not_all_shown_bookmarks_carplay"), detailText: L("switch_to_phone_bookmarks_carplay"))
      cropWarning.isEnabled = false
      items.append(cropWarning)
    }
    return [CPListSection(items: items)]
  }

  private class func buildSearchResultsSections(_ results: [MWMCarPlaySearchResultObject]) -> [CPListSection] {
    var items = [CPListItem]()
    for object in results {
      let item = CPListItem(text: object.title, detailText: object.address)
      item.userInfo = ListItemInfo(type: CPConstants.ListItemType.searchResults,
                                   metadata: SearchResultInfo(originalRow: object.originalRow))
      items.append(item)
    }
    return [CPListSection(items: items)]
  }


  // MARK: - CPBarButton builder
  private class func buildBarButton(type: BarButtonType, action: ((CPBarButton) -> Void)?) -> CPBarButton {
    switch type {
    case .bookmarks:
      return CPBarButton(image: UIImage(systemName: "list.star")!, handler: action)
    case .search:
      return CPBarButton(image: UIImage(systemName: "keyboard.fill")!, handler: action)
    }
  }
}
