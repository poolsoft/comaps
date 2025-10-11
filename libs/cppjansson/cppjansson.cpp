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
// NOTE: GetJSONObligatoryField / GetJSONOptionalField overloads
// were moved inline into the header (cppjansson.hpp) to allow
// template instantiations in other translation units to resolve
// without relying on a single compiled object file.

// JSONIsNull moved inline to header.

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

// LoadFromString moved inline to header to provide a centralized,
// inlined parser with encoding fallbacks for all translation units.

}  // namespace base

// primitive FromJSON overloads for double and bool are defined inline
// in the header to allow template instantiations from different
// translation units to resolve without link errors.

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
