#include "storage/map_files_downloader_with_ping.hpp"

#include "storage/pinger.hpp"

#include "platform/platform.hpp"

#include "base/assert.hpp"

namespace storage
{
void MapFilesDownloaderWithPing::GetMetaConfig(MetaConfigCallback const & callback)
{
  ASSERT(callback , ());

  MetaConfig metaConfig = LoadMetaConfig();
  CHECK(!metaConfig.m_serversList.empty(), ());

  // Sort the list of servers by latency.
  /// @todo(pastk:: actually the sort order is used only in MapFilesDownloader::DownloadAsString()
  /// to get e.g. countries.txt but this code is not enabled at the moment.
  auto const sorted = Pinger::ExcludeUnavailableAndSortEndpoints(metaConfig.m_serversList);
  // Keep the original list if all servers are unavailable.
  if (!sorted.empty())
    metaConfig.m_serversList = sorted;
  callback(metaConfig);
}
}  // namespace storage
