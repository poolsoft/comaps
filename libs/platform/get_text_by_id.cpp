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
// Try to convert ISO-8859-1 / Latin1 encoded bytes into a valid UTF-8 string.
// Lightweight fallback for packaged localization files that may be encoded
// in Latin1 instead of UTF-8. Each byte >= 0x80 is converted into a two-
// byte UTF-8 sequence so the JSON parser can succeed on formerly-invalid
// input.
string Latin1ToUtf8(string const & s)
{
  string out;
  out.reserve(s.size() * 2);
  for (size_t i = 0; i < s.size(); ++i)
  {
    unsigned char c = static_cast<unsigned char>(s[i]);
    if (c < 0x80)
      out.push_back(static_cast<char>(c));
    else
    {
      out.push_back(static_cast<char>(0xC0 | (c >> 6)));
      out.push_back(static_cast<char>(0x80 | (c & 0x3F)));
    }
  }
  return out;
}

// Minimal embedded fallback JSON used when both locale-specific and
// packaged default localization files are unavailable or invalid. This
// ensures the native code can continue running without aborting on
// missing or corrupted resource files.
char const kEmbeddedDefaultLocalizeJson[] = "{}";
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
      // Prepare hex sample for logs
      std::ostringstream oss;
      size_t sampleLen = std::min<size_t>(jsonBuffer.size(), 128);
      for (size_t i = 0; i < sampleLen; ++i)
      {
        oss << std::hex << std::setfill('0') << std::setw(2)
            << (static_cast<unsigned int>(static_cast<unsigned char>(jsonBuffer[i]))) << ' ';
      }
  // Log as WARNING first so we can attempt a non-fatal fallback before
  // escalating to an error that triggers the abort behavior in some
  // Android builds.
  LOG(LWARNING, ("JSON parse error for locale:", localeName, "file:", relPath, "error:", exJson.what(), "sample_bytes:", oss.str()));

      // Mitigation: try interpreting the bytes as ISO-8859-1 (Latin1) and
      // convert to UTF-8, then attempt parsing again. If conversion+
      // parse succeeds we keep the converted buffer and proceed; otherwise
      // preserve original behavior and rethrow to trigger fallback logic.
      try
      {
  LOG(LINFO, ("Attempting Latin1->UTF8 fallback for:", relPath));
        string converted = Latin1ToUtf8(jsonBuffer);
        // Try parsing converted buffer
        try
        {
          base::Json rootConverted(converted.c_str());
          (void)rootConverted.get();
          // Success — use converted buffer from now on
          jsonBuffer.swap(converted);
          LOG(LINFO, ("Latin1->UTF8 fallback succeeded for:", relPath));
          // do not rethrow — outer code will continue using jsonBuffer
        }
        catch (base::Json::Exception const & ex2)
        {
          // Fallback parsing failed — log as WARNING so we can try higher-
          // level fallback (embedded default) instead of aborting the
          // process on platforms where ERROR logs trigger aborts.
          LOG(LWARNING, ("Latin1->UTF8 fallback parse failed for:", relPath,
                         "original_error:", exJson.what(), "fallback_error:", ex2.what(), "sample_bytes:", oss.str()));
          MYTHROW(RootException, ("Invalid JSON in file", relPath, exJson.what()));
        }
      }
      catch (std::exception const & e)
      {
        LOG(LWARNING, ("Latin1->UTF8 fallback failed with exception:", e.what(), "file:", relPath));
        MYTHROW(RootException, ("Invalid JSON in file", relPath, exJson.what()));
      }
    }
  }
  catch (RootException const & ex)
  {
    LOG(LWARNING, ("Can't open or parse", localeName, "localization file:", relPath, ex.what()));
    // If we're already attempting the default language, use an embedded
    // minimal JSON as a last-resort fallback to avoid aborting the
    // application due to missing/corrupted resources.
    if (localeName == kDefaultLanguage)
    {
      LOG(LWARNING, ("Using embedded default localization for:", localeName));
      jsonBuffer = string(kEmbeddedDefaultLocalizeJson);
      return true;
    }
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
