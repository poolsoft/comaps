
#include "feature_helpers/feature_charge_sockets.hpp"

#include "base/logging.hpp"
#include "base/string_utils.hpp"

#include <algorithm>
#include <iomanip>  // for formatting doubles
#include <sstream>  // for std::to_string alternative
#include <string_view>
#include <vector>

// helper to format doubles (avoids trailing zeros)
inline std::string to_string_trimmed(double value, int precision = 2)
{
  std::ostringstream oss;
  oss << std::fixed << std::setprecision(precision) << value;
  auto str = oss.str();

  // Remove trailing zeros
  auto pos = str.find('.');
  if (pos != std::string::npos)
  {
    // erase trailing zeros
    while (!str.empty() && str.back() == '0')
      str.pop_back();
    // if we end with a '.', remove it too
    if (!str.empty() && str.back() == '.')
      str.pop_back();
  }
  return str;
}

// we use a custom tokenizer, as strings::Tokenize discards empty
// tokens and we want to keep them:
//
// Example:
//    "a||" → ["a", "", ""]
//    "b|0|" → ["b", "0", ""]
//    "c||34" → ["c", "", "34"]
//    "d|43|54" → ["d", "43", "54"]
std::vector<std::string> tokenize(std::string_view s, char delim = '|')
{
  std::vector<std::string> tokens;
  size_t start = 0;
  while (true)
  {
    size_t pos = s.find(delim, start);
    if (pos == std::string_view::npos)
    {
      tokens.emplace_back(s.substr(start));
      break;
    }
    tokens.emplace_back(s.substr(start, pos - start));
    start = pos + 1;
  }
  return tokens;
}

ChargeSocketsHelper::ChargeSocketsHelper(std::string const & socketsList) : m_dirty(false)
{
  if (socketsList.empty())
    return;

  auto tokens = tokenize(socketsList, ';');

  for (auto token : tokens)
  {
    if (token.empty())
      continue;

    auto fields = tokenize(token, '|');

    if (fields.size() != 3)
      continue;  // invalid entry, skip

    ChargeSocketDescriptor desc;

    if (std::find(SUPPORTED_TYPES.begin(), SUPPORTED_TYPES.end(), fields[0]) != SUPPORTED_TYPES.end())
      desc.type = fields[0];
    else
      desc.type = UNKNOWN;

    try
    {
      desc.count = std::stoi(std::string(fields[1]));
    }
    catch (...)
    {
      desc.count = 0;
    }

    if (fields.size() >= 3)
    {
      try
      {
        desc.power = std::stod(std::string(fields[2]));
      }
      catch (...)
      {
        desc.power = 0;
      }
    }
    else
      desc.power = 0;

    m_chargeSockets.push_back(desc);
  }
  m_dirty = true;
}

void ChargeSocketsHelper::AddSocket(std::string const & type, unsigned int count, double power)
{
  if (power < 0)
  {
    LOG(LWARNING, ("Invalid socket power. Must be >= 0:", power));
    return;
  }

  m_chargeSockets.push_back({type, count, power});
  m_dirty = true;
}

void ChargeSocketsHelper::AggregateChargeSocketKey(std::string const & k, std::string const & v)
{
  auto keys = strings::Tokenize(k, ":");
  ASSERT(keys[0] == "socket", ());  // key must start with "socket:"
  if (keys.size() < 2 || keys.size() > 3)
  {
    LOG(LWARNING, ("Invalid socket key:", k));
    return;
  }

  std::string type(keys[1]);

  bool isOutput = false;
  if (keys.size() == 3)
  {
    if (keys[2] == "output")
      isOutput = true;
    else
      return;  // ignore other suffixes
  }

  // normalize type if needed
  // based on recommandations from https://wiki.openstreetmap.org/wiki/Key:socket:*
  static std::unordered_map<std::string, std::string> const kTypeMap = {
      // also sometimes used in EU for 'type2_combo'
      // -> those cases would require correcting the OSM tagging
      {"tesla_supercharger", "nacs"},
      {"tesla_destination", "nacs"},
      {"tesla_standard", "nacs"},
      {"tesla", "nacs"},
      {"tesla_supercharger_ccs", "type2_combo"},
      {"ccs", "type2_combo"},
      {"type1_cable", "type1"},
  };

  auto itMap = kTypeMap.find(type);
  if (itMap != kTypeMap.end())
    type = itMap->second;

  if (std::find(SUPPORTED_TYPES.begin(), SUPPORTED_TYPES.end(), type) == SUPPORTED_TYPES.end())
    type = UNKNOWN;

  // find or create descriptor
  auto it = std::find_if(m_chargeSockets.begin(), m_chargeSockets.end(),
                         [&](ChargeSocketDescriptor const & d) { return d.type == type; });

  if (it == m_chargeSockets.end())
  {
    m_chargeSockets.push_back({type, 0, 0});
    it = std::prev(m_chargeSockets.end());
  }

  ASSERT(v.size() > 0, ("empty value for socket key!"));

  if (!isOutput)
  {
    if (v == "yes")
    {
      it->count = 0;
    }
    else
    {
      // try to parse count as a number
      int count;
      try
      {
        count = std::stoi(v);
        if (count <= 0)
        {
          LOG(LWARNING, ("Invalid socket count. Removing this socket.", ""));
          // TODO(skadge): incorrect behaviour if invalid count while modifying an existing socket that is not the last
          // one!
          m_chargeSockets.pop_back();
          return;
        }
      }
      catch (...)
      {
        // ignore sockets with invalid counts (ie, can not be parsed to int)
        // note that if a valid power output is later set for this socket,
        // the socket will be re-created with a default count of 'y'
        LOG(LWARNING, ("Invalid count of charging socket. Removing it.", v));
        // TODO(skadge): incorrect behaviour if invalid count while modifying an existing socket that is not the last
        // one!
        m_chargeSockets.pop_back();
        return;
      }
      it->count = count;
    }
  }
  else  // isOutput == true => parse output power
  {
    // example value string: "44;22kW;11kva;7400w"

    std::string powerValues = strings::MakeLowerCase(v);

    // replace all occurances of 'VA' by the more standard 'W' unit
    size_t pos = powerValues.find("va");
    while (pos != powerValues.npos)
    {
      powerValues.replace(pos, 2, "w");
      pos = powerValues.find("va", pos + 1);
    }

    // if a given socket type is present several times in the same charging
    // station with different power outputs, the power outputs would be concatenated
    // with ';'
    auto powerTokens = strings::Tokenize(powerValues, ";/");

    // TODO: for now, we only handle the *first* provided
    // power output.
    std::string num(powerTokens[0]);
    strings::Trim(num);

    if (num == "unknown")
    {
      it->power = 0;
      m_dirty = true;
      return;
    }

    enum PowerUnit
    {
      WATT,
      KILOWATT,
      MEGAWATT
    };
    PowerUnit unit = KILOWATT;  // if no unit, kW are assumed

    if (num.size() > 2)
    {
      // do we have a unit?
      if (num.back() == 'w')
      {
        unit = WATT;
        num.pop_back();
        if (num.back() == 'k')
        {
          unit = KILOWATT;
          num.pop_back();
        }
        else if (num.back() == 'm')
        {
          unit = MEGAWATT;
          num.pop_back();
        }
      }
    }

    strings::Trim(num);
    double value;
    try
    {
      value = std::stod(num);
      if (value <= 0)
      {
        LOG(LWARNING, ("Invalid charging socket power value:", v));
        // TODO(skadge): incorrect behaviour if invalid count while modifying an existing socket that is not the last
        // one!
        m_chargeSockets.pop_back();
        return;
      }

      std::ostringstream oss;
      switch (unit)
      {
      case WATT: value = value / 1000.; break;
      case MEGAWATT: value = value * 1000; break;
      case KILOWATT: break;
      }
    }
    catch (...)
    {
      LOG(LWARNING, ("Invalid charging socket power value:", v));
      // TODO(skadge): incorrect behaviour if invalid count while modifying an existing socket that is not the last
      // one!
      m_chargeSockets.pop_back();
      return;
    }

    it->power = value;
  }

  m_dirty = true;
}

ChargeSocketDescriptors ChargeSocketsHelper::GetSockets()
{
  if (m_dirty)
    Sort();

  return m_chargeSockets;
}

std::string ChargeSocketsHelper::ToString()
{
  if (m_dirty)
    Sort();

  std::ostringstream oss;

  for (size_t i = 0; i < m_chargeSockets.size(); ++i)
  {
    auto const & desc = m_chargeSockets[i];

    oss << desc.type << "|";
    if (desc.count > 0)
      oss << desc.count;
    oss << "|";
    if (desc.power > 0)
      oss << desc.power;

    if (i + 1 < m_chargeSockets.size())
      oss << ";";
  }
  return oss.str();
}

OSMKeyValues ChargeSocketsHelper::GetOSMKeyValues()
{
  if (m_dirty)
    Sort();

  std::vector<std::pair<std::string, std::string>> result;

  for (auto const & s : m_chargeSockets)
  {
    // Only produce if type is non-empty
    if (!s.type.empty())
    {
      // socket.<type> = count
      if (s.count > 0)
      {
        result.emplace_back("socket:" + s.type, std::to_string(s.count));
      }
      else if (s.count == 0)
      {
        // special "yes" meaning present, but unknown count
        result.emplace_back("socket:" + s.type, "yes");
      }

      // socket.<type>.output = power
      if (s.power > 0.0)
        result.emplace_back("socket:" + s.type + ":output", to_string_trimmed(s.power));
    }
  }

  return result;
}

void ChargeSocketsHelper::Sort()
{
  size_t const unknownTypeOrder = SUPPORTED_TYPES.size();

  std::sort(m_chargeSockets.begin(), m_chargeSockets.end(),
            [&](ChargeSocketDescriptor const & a, ChargeSocketDescriptor const & b)
  {
    auto const itA = std::find(SUPPORTED_TYPES.begin(), SUPPORTED_TYPES.end(), a.type);
    auto const orderA = (itA == SUPPORTED_TYPES.end()) ? unknownTypeOrder : std::distance(SUPPORTED_TYPES.begin(), itA);
    auto const itB = std::find(SUPPORTED_TYPES.begin(), SUPPORTED_TYPES.end(), b.type);
    auto const orderB = (itB == SUPPORTED_TYPES.end()) ? unknownTypeOrder : std::distance(SUPPORTED_TYPES.begin(), itB);

    if (orderA != orderB)
      return orderA < orderB;
    return a.power > b.power;  // Sort by power in descending order for sockets of the same type
  });

  m_dirty = false;
}
