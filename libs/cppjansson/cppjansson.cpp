#include "cppjansson.hpp"

#include <type_traits>
#include "base/logging.hpp"
#include <algorithm>
#include <string>

namespace
{
template <typename T>
std::string FromJSONToString(json_t const * root)
{
  T result;
  FromJSON(root, result);
  // TODO(AB): Is std::to_string faster?
  return strings::to_string(result);
}

// Convert ISO-8859-1 / Latin1 bytes to UTF-8. This mirrors the lightweight
// conversion implemented in platform code and centralizes fallback logic
// for JSON parsing so callers don't need to duplicate it.
std::string Latin1ToUtf8(std::string const & s)
{
  std::string out;
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
}  // namespace

namespace base
{
json_t * GetJSONObligatoryField(json_t * root, std::string const & field)
{
  return GetJSONObligatoryField(root, field.c_str());
}

json_t * GetJSONObligatoryField(json_t * root, char const * field)
{
  return const_cast<json_t *>(GetJSONObligatoryField(const_cast<json_t const *>(root), field));
}

json_t const * GetJSONObligatoryField(json_t const * root, std::string const & field)
{
  return GetJSONObligatoryField(root, field.c_str());
}

json_t const * GetJSONObligatoryField(json_t const * root, char const * field)
{
  auto * value = base::GetJSONOptionalField(root, field);
  if (!value)
    MYTHROW(base::Json::Exception, ("Obligatory field", field, "is absent."));
  return value;
}

json_t * GetJSONOptionalField(json_t * root, std::string const & field)
{
  return GetJSONOptionalField(root, field.c_str());
}

json_t * GetJSONOptionalField(json_t * root, char const * field)
{
  return const_cast<json_t *>(GetJSONOptionalField(const_cast<json_t const *>(root), field));
}

json_t const * GetJSONOptionalField(json_t const * root, std::string const & field)
{
  return GetJSONOptionalField(root, field.c_str());
}

json_t const * GetJSONOptionalField(json_t const * root, char const * field)
{
  if (!json_is_object(root))
    MYTHROW(base::Json::Exception, ("Bad json object while parsing", field));
  return json_object_get(root, field);
}

bool JSONIsNull(json_t const * root)
{
  return json_is_null(root);
}

std::string DumpToString(JSONPtr const & json, size_t flags)
{
  std::string result;
  size_t size = json_dumpb(json.get(), nullptr, 0, flags);
  if (size == 0)
    MYTHROW(base::Json::Exception, ("Zero size JSON while serializing"));

  result.resize(size);
  if (size != json_dumpb(json.get(), &result.front(), size, flags))
    MYTHROW(base::Json::Exception, ("Wrong size JSON written while serializing"));

  return result;
}

JSONPtr LoadFromString(std::string const & str)
{
  json_error_t jsonError = {};
  json_t * result = json_loads(str.c_str(), 0, &jsonError);
  if (result)
    return JSONPtr(result);

  std::string err = jsonError.text ? jsonError.text : std::string();
  // If parser failed due to decoding/invalid UTF-8 bytes, try a Latin1
  // -> UTF-8 conversion and parse again. This helps with assets that
  // accidentally were encoded in ISO-8859-1.
  if (!err.empty() && (err.find("unable to decode") != std::string::npos ||
                       err.find("invalid") != std::string::npos ||
                       err.find("UTF-8") != std::string::npos))
  {
    LOG(LINFO, ("JSON parse failed, attempting Latin1->UTF8 fallback. Error:", err));
    try
    {
      std::string converted = Latin1ToUtf8(str);
      json_error_t jsonError2 = {};
      json_t * result2 = json_loads(converted.c_str(), 0, &jsonError2);
      if (result2)
      {
        LOG(LINFO, ("JSON parse success after Latin1->UTF8 fallback."));
        return JSONPtr(result2);
      }
      std::string err2 = jsonError2.text ? jsonError2.text : std::string();
      LOG(LWARNING, ("Latin1 fallback parse failed. original:", err, "fallback:", err2));
      MYTHROW(base::Json::Exception, (err + " / " + err2));
    }
    catch (std::exception const & e)
    {
      LOG(LWARNING, ("Latin1->UTF8 conversion failed:", e.what()));
      MYTHROW(base::Json::Exception, (err));
    }
  }

  MYTHROW(base::Json::Exception, (err));
}

}  // namespace base

void FromJSON(json_t const * root, double & result)
{
  if (!json_is_number(root))
    MYTHROW(base::Json::Exception, ("Object must contain a json number."));
  result = json_number_value(root);
}

void FromJSON(json_t const * root, bool & result)
{
  if (!json_is_true(root) && !json_is_false(root))
    MYTHROW(base::Json::Exception, ("Object must contain a boolean value."));
  result = json_is_true(root);
}

std::string FromJSONToString(json_t const * root)
{
  if (json_is_string(root))
    return FromJSONToString<std::string>(root);

  if (json_is_integer(root))
    return FromJSONToString<json_int_t>(root);

  if (json_is_real(root))
    return FromJSONToString<double>(root);

  if (json_is_boolean(root))
    return FromJSONToString<bool>(root);

  MYTHROW(base::Json::Exception, ("Unexpected json type"));
}

namespace std
{
void FromJSON(json_t const * root, std::string & result)
{
  if (!json_is_string(root))
    MYTHROW(base::Json::Exception, ("The field must contain a json string."));
  result = json_string_value(root);
}
}  // namespace std

namespace strings
{
void FromJSON(json_t const * root, UniString & result)
{
  std::string s;
  FromJSON(root, s);
  result = MakeUniString(s);
}

base::JSONPtr ToJSON(UniString const & s)
{
  return ToJSON(ToUtf8(s));
}
}  // namespace strings
