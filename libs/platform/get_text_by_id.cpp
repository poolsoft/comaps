#include "platform/get_text_by_id.hpp"

#include "platform/platform.hpp"

#include "base/file_name_utils.hpp"
#include "base/logging.hpp"

#include "cppjansson/cppjansson.hpp"

#include <algorithm>
#include <sstream>
#include <iomanip>


namespace platform
{
using std::string;

namespace
{
string const kDefaultLanguage = "en";

string GetTextSourceString(platform::TextSource textSource)
{
  switch (textSource)
  {
  case platform::TextSource::TtsSound: return string("sound-strings");
  case platform::TextSource::Countries: return string("countries-strings");
  }
  ASSERT(false, ());
  return string();
}
}  // namespace

bool GetJsonBuffer(platform::TextSource textSource, string const & localeName, string & jsonBuffer)
{
  string const relPath = base::JoinPath(GetTextSourceString(textSource), localeName + ".json", "localize.json");
  string const pathToJson = base::JoinPath(GetTextSourceString(textSource), localeName + ".json", "localize.json");

  // Try reading file using platform reader. We add extra logging to detect
  // which source (writable/resources/settings/full path) provided the file
  // and to output a sample of the first bytes when JSON decoding fails.
  try
  {
    jsonBuffer.clear();
    // Read raw string
    auto reader = GetPlatform().GetReader(relPath);
    // Log the resolved path for debugging (works in DEBUG builds via DbgLogger too)
    try
    {
      string resolved = GetPlatform().ReadPathForFile(relPath);
      LOG(LINFO, ("Resolved localization file path:", resolved));
    }
    catch (RootException const &)
    {
      // ReadPathForFile may throw if not found in scope, ignore here
    }

    reader->ReadAsString(jsonBuffer);

    // Quick UTF-8 sanity check: attempt to parse JSON and if it fails, log a hexdump sample
    try
    {
      base::Json root(jsonBuffer.c_str());
      (void)root.get();
    }
    catch (base::Json::Exception const & exJson)
    {
      // Prepare hex sample
      std::ostringstream oss;
      size_t sampleLen = std::min<size_t>(jsonBuffer.size(), 128);
      for (size_t i = 0; i < sampleLen; ++i)
      {
        oss << std::hex << std::setfill('0') << std::setw(2) << (static_cast<unsigned int>(static_cast<unsigned char>(jsonBuffer[i]))) << ' ';
      }
      LOG(LERROR, ("JSON parse error for locale:", localeName, "file:", relPath, "error:", exJson.what(), "sample_bytes:", oss.str()));
      // Re-throw as RootException to make the caller try default language
      MYTHROW(RootException, ("Invalid JSON in file", relPath, exJson.what()));
    }
  }
  catch (RootException const & ex)
  {
    LOG(LWARNING, ("Can't open or parse", localeName, "localization file:", relPath, ex.what()));
    return false;  // No json file for localeName or it failed parsing
  }
  return true;
}

TGetTextByIdPtr GetTextById::Create(string const & jsonBuffer, string const & localeName)
{
  TGetTextByIdPtr result(new GetTextById(jsonBuffer, localeName));
  if (!result->IsValid())
  {
    ASSERT(false, ("Can't create a GetTextById instance from a json file. localeName=", localeName));
    return nullptr;
  }
  return result;
}

TGetTextByIdPtr GetTextByIdFactory(TextSource textSource, string const & localeName)
{
  string jsonBuffer;
  if (GetJsonBuffer(textSource, localeName, jsonBuffer))
    return GetTextById::Create(jsonBuffer, localeName);

  if (GetJsonBuffer(textSource, kDefaultLanguage, jsonBuffer))
    return GetTextById::Create(jsonBuffer, kDefaultLanguage);

  ASSERT(false, ("Can't find translate for default language. (Lang:", localeName, ")"));
  return nullptr;
}

TGetTextByIdPtr ForTestingGetTextByIdFactory(string const & jsonBuffer, string const & localeName)
{
  return GetTextById::Create(jsonBuffer, localeName);
}

GetTextById::GetTextById(string const & jsonBuffer, string const & localeName) : m_locale(localeName)
{
  if (jsonBuffer.empty())
  {
    ASSERT(false, ("No json files found."));
    return;
  }

  base::Json root(jsonBuffer.c_str());
  if (root.get() == nullptr)
  {
    ASSERT(false, ("Cannot parse the json file."));
    return;
  }

  char const * key = nullptr;
  json_t * value = nullptr;
  json_object_foreach(root.get(), key, value)
  {
    ASSERT(key, ());
    ASSERT(value, ());
    char const * const valueStr = json_string_value(value);
    ASSERT(valueStr, ());
    m_localeTexts[key] = valueStr;
  }
  ASSERT_EQUAL(m_localeTexts.size(), json_object_size(root.get()), ());
}

string GetTextById::operator()(string const & textId) const
{
  auto const textIt = m_localeTexts.find(textId);
  if (textIt == m_localeTexts.end())
    return string();
  return textIt->second;
}

TTranslations GetTextById::GetAllSortedTranslations() const
{
  TTranslations all;
  all.reserve(m_localeTexts.size());
  for (auto const & tr : m_localeTexts)
    all.emplace_back(tr.first, tr.second);
  using TValue = TTranslations::value_type;
  sort(all.begin(), all.end(), [](TValue const & v1, TValue const & v2) { return v1.second < v2.second; });
  return all;
}
}  // namespace platform
