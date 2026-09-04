-- 달림(Dallim) 코스 생성용 보행자(foot) 프로필 — OSRM 표준 foot.lua(v5, api_version 2) 기반.
--
-- 배경 (docs/01-feature-spec.md 2.2.C, docs/02-api-spec.md 8.2 "알려진 한계"):
-- 표준 foot.lua는 모든 도로 유형에 동일한 walking_speed를 매겨서, "좁은 골목·인도 없는
-- 차도·시야 확보 안 되는 구간"과 "인도/공원/하천변 산책로"를 구분하지 않고 순수 최단거리로만
-- 라우팅했다. 이 프로필은 도로 유형(highway=*)과 보조 태그(sidewalk, lit)에 따라 speed를
-- 차등화해서, 러닝하기 안전/쾌적한 구간을 우선하고 큰길/인도 없는 구간을 기피하게 만든다.
--
-- 중요: OSRM은 실제 보행 속도를 바꾸는 게 아니라 weight_name='duration'이라 speed를
-- "거리당 비용"의 대리 지표로 쓴다 — 낮은 speed를 매길수록 그 구간의 weight(비용)가 커져서
-- 최단-비용 경로 탐색이 자연히 그 구간을 피해가게 된다. 실제 예상 소요시간(estimatedMinutes)
-- 계산에는 영향 없음 — 그건 DiscoveryService가 거리 기준 ASSUMED_MINUTES_PER_KM으로 별도 계산한다.
--
-- 데이터 기반 필터링(예: 폭·보차분리 GIS 데이터)까지는 아직 아니고, OSM 태그만으로 판단하는
-- 1단계 근사치다 — sidewalk/lit 태그가 없는 way는 도로 유형만으로 판단(중립~기피 사이).

api_version = 2

Set = require('lib/set')
Sequence = require('lib/sequence')
Handlers = require("lib/way_handlers")
find_access_tag = require("lib/access").find_access_tag

local walking_speed = 5

-- 안전 기준 가중치 배율 (walking_speed 대비) — 값 자체의 물리적 의미보다 "상대적 선호도"로 읽는다.
local PREFERRED_MULTIPLIER             = 1.2   -- 인도/공원 보행로/보행자 전용도로
local NEUTRAL_MULTIPLIER               = 1.0   -- 판단 보류(주택가 이면도로 등) — 기존 기본값과 동일
local DISCOURAGED_MULTIPLIER           = 0.55  -- 차량 통행 많고 인도 없을 가능성이 높은 중간 규모 도로
local STRONGLY_DISCOURAGED_MULTIPLIER  = 0.35  -- 간선/고속 성격의 큰길
local SIDEWALK_YES_OVERRIDE_MULTIPLIER = 1.0   -- sidewalk 명시 시 큰길이어도 기피 배율을 되돌림
local SIDEWALK_NO_PENALTY              = 0.7   -- sidewalk=no/none 명시 시 추가 페널티
local UNLIT_PENALTY                    = 0.85  -- lit=no(조명 없음, 시야 확보 안 됨) 추가 페널티

function setup()
  return {
    properties = {
      weight_name                   = 'duration',
      max_speed_for_map_matching    = 40/3.6, -- kmph -> m/s
      call_tagless_node_function    = false,
      traffic_light_penalty         = 2,
      u_turn_penalty                = 2,
      continue_straight_at_waypoint = false,
      use_turn_restrictions         = false,
    },

    default_mode            = mode.walking,
    default_speed           = walking_speed,
    oneway_handling         = 'specific',     -- respect 'oneway:foot' but not 'oneway'

    barrier_blacklist = Set {
      'yes',
      'wall',
      'fence'
    },

    access_tag_whitelist = Set {
      'yes',
      'foot',
      'permissive',
      'designated'
    },

    access_tag_blacklist = Set {
      'no',
      'agricultural',
      'forestry',
      'private',
      'delivery',
    },

    restricted_access_tag_list = Set { },

    restricted_highway_whitelist = Set { },

    construction_whitelist = Set {},

    access_tags_hierarchy = Sequence {
      'foot',
      'access'
    },

    -- tags disallow access to in combination with highway=service
    service_access_tag_blacklist = Set { },

    restrictions = Sequence {
      'foot'
    },

    -- list of suffixes to suppress in name change instructions
    suffix_list = Set {
      'N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW', 'North', 'South', 'West', 'East'
    },

    avoid = Set {
      'impassable'
    },

    -- 도로 유형별 기본 speed — 표준 foot.lua와 달리 유형별로 차등화했다(위 상수 설명 참고).
    speeds = Sequence {
      highway = {
        -- 안전/쾌적: 인도, 공원·하천변 산책로, 보행자 전용
        footway         = walking_speed * PREFERRED_MULTIPLIER,
        pedestrian      = walking_speed * PREFERRED_MULTIPLIER,
        path            = walking_speed * PREFERRED_MULTIPLIER,
        living_street   = walking_speed * PREFERRED_MULTIPLIER,
        track           = walking_speed * PREFERRED_MULTIPLIER,
        steps           = walking_speed * NEUTRAL_MULTIPLIER,
        pier            = walking_speed * NEUTRAL_MULTIPLIER,

        -- 판단 보류: 주택가 이면도로 — sidewalk/lit 태그가 있으면 아래에서 추가 보정
        residential     = walking_speed * NEUTRAL_MULTIPLIER,
        unclassified    = walking_speed * NEUTRAL_MULTIPLIER,
        road            = walking_speed * NEUTRAL_MULTIPLIER,
        service         = walking_speed * NEUTRAL_MULTIPLIER,

        -- 기피: 차량 통행 많고 인도 없을 가능성이 높은 중간 규모 도로
        tertiary        = walking_speed * DISCOURAGED_MULTIPLIER,
        tertiary_link   = walking_speed * DISCOURAGED_MULTIPLIER,
        secondary       = walking_speed * DISCOURAGED_MULTIPLIER,
        secondary_link  = walking_speed * DISCOURAGED_MULTIPLIER,

        -- 강하게 기피: 간선/고속 성격의 큰길
        primary         = walking_speed * STRONGLY_DISCOURAGED_MULTIPLIER,
        primary_link    = walking_speed * STRONGLY_DISCOURAGED_MULTIPLIER,
        trunk           = walking_speed * STRONGLY_DISCOURAGED_MULTIPLIER,
        trunk_link      = walking_speed * STRONGLY_DISCOURAGED_MULTIPLIER,
      },

      railway = {
        platform        = walking_speed * NEUTRAL_MULTIPLIER
      },

      amenity = {
        parking         = walking_speed * NEUTRAL_MULTIPLIER,
        parking_entrance= walking_speed * NEUTRAL_MULTIPLIER
      },

      man_made = {
        pier            = walking_speed * NEUTRAL_MULTIPLIER
      },

      leisure = {
        track           = walking_speed * PREFERRED_MULTIPLIER -- 공원 내 산책/운동 트랙
      }
    },

    route_speeds = {
      ferry = 5
    },

    bridge_speeds = {
    },

    surface_speeds = {
      fine_gravel =   walking_speed*0.75,
      gravel =        walking_speed*0.75,
      pebblestone =   walking_speed*0.75,
      mud =           walking_speed*0.5,
      sand =          walking_speed*0.5
    },

    tracktype_speeds = {
    },

    smoothness_speeds = {
    }
  }
end

function process_node(profile, node, result)
  -- parse access and barrier tags
  local access = find_access_tag(node, profile.access_tags_hierarchy)
  if access then
    if profile.access_tag_blacklist[access] then
      result.barrier = true
    end
  else
    local barrier = node:get_value_by_key("barrier")
    if barrier then
      --  make an exception for rising bollard barriers
      local bollard = node:get_value_by_key("bollard")
      local rising_bollard = bollard and "rising" == bollard

      if profile.barrier_blacklist[barrier] and not rising_bollard then
        result.barrier = true
      end
    end
  end

  -- check if node is a traffic light
  local tag = node:get_value_by_key("highway")
  if "traffic_signals" == tag then
    result.traffic_lights = true
  end
end

-- sidewalk/lit 태그 기반 추가 보정 — 표준 handler 시퀀스가 끝난 뒤(speed/surface 확정 후) 적용해서
-- 최종 result.forward_speed/backward_speed를 한 번 더 조정한다. 표준 WayHandlers에 없는,
-- 이 프로필만의 안전 기준 보정이라 별도 함수로 분리했다.
local BIG_ROAD_HIGHWAYS = Set {
  'primary', 'primary_link', 'trunk', 'trunk_link',
  'secondary', 'secondary_link', 'tertiary', 'tertiary_link',
}

function apply_safety_tag_adjustments(profile, way, result, data)
  local highway = data.highway
  if not highway then
    return
  end

  local function scale(multiplier)
    if result.forward_speed and result.forward_speed > 0 then
      result.forward_speed = result.forward_speed * multiplier
    end
    if result.backward_speed and result.backward_speed > 0 then
      result.backward_speed = result.backward_speed * multiplier
    end
  end

  -- sidewalk=* : 인도 유무가 명시된 경우, 큰길 기피 배율을 보정한다.
  local sidewalk = way:get_value_by_key('sidewalk')
  if sidewalk then
    if BIG_ROAD_HIGHWAYS[highway] and
       (sidewalk == 'both' or sidewalk == 'left' or sidewalk == 'right' or
        sidewalk == 'yes' or sidewalk == 'separate') then
      -- 인도가 있다고 명시된 큰길은 기피할 이유가 없어졌으니 중립 수준으로 되돌린다.
      if result.forward_speed and result.forward_speed > 0 then
        result.forward_speed = walking_speed * SIDEWALK_YES_OVERRIDE_MULTIPLIER
      end
      if result.backward_speed and result.backward_speed > 0 then
        result.backward_speed = walking_speed * SIDEWALK_YES_OVERRIDE_MULTIPLIER
      end
    elseif sidewalk == 'no' or sidewalk == 'none' then
      -- 인도 없음이 명시적으로 태깅된 경우 도로 유형과 무관하게 추가로 기피한다.
      scale(SIDEWALK_NO_PENALTY)
    end
  end

  -- lit=no : 조명 없음(시야 확보 안 되는 구간) 추가 페널티.
  local lit = way:get_value_by_key('lit')
  if lit == 'no' then
    scale(UNLIT_PENALTY)
  end
end

-- main entry point for processsing a way
function process_way(profile, way, result)
  -- the intial filtering of ways based on presence of tags
  -- affects processing times significantly, because all ways
  -- have to be checked.
  -- to increase performance, prefetching and intial tag check
  -- is done in directly instead of via a handler.

  -- in general we should  try to abort as soon as
  -- possible if the way is not routable, to avoid doing
  -- unnecessary work. this implies we should check things that
  -- commonly forbids access early, and handle edge cases later.

  -- data table for storing intermediate values during processing
  local data = {
    -- prefetch tags
    highway = way:get_value_by_key('highway'),
    bridge = way:get_value_by_key('bridge'),
    route = way:get_value_by_key('route'),
    leisure = way:get_value_by_key('leisure'),
    man_made = way:get_value_by_key('man_made'),
    railway = way:get_value_by_key('railway'),
    platform = way:get_value_by_key('platform'),
    amenity = way:get_value_by_key('amenity'),
    public_transport = way:get_value_by_key('public_transport')
  }

  -- perform an quick initial check and abort if the way is
  -- obviously not routable. here we require at least one
  -- of the prefetched tags to be present, ie. the data table
  -- cannot be empty
  if next(data) == nil then     -- is the data table empty?
    return
  end

  local handlers = Sequence {
    -- set the default mode for this profile. if can be changed later
    -- in case it turns we're e.g. on a ferry
    WayHandlers.default_mode,

    -- check various tags that could indicate that the way is not
    -- routable. this includes things like status=impassable,
    -- toll=yes and oneway=reversible
    WayHandlers.blocked_ways,

    -- determine access status by checking our hierarchy of
    -- access tags, e.g: motorcar, motor_vehicle, vehicle
    WayHandlers.access,

    -- check whether forward/backward directons are routable
    WayHandlers.oneway,

    -- check whether forward/backward directons are routable
    WayHandlers.destinations,

    -- check whether we're using a special transport mode
    WayHandlers.ferries,
    WayHandlers.movables,

    -- compute speed taking into account way type, maxspeed tags, etc.
    WayHandlers.speed,
    WayHandlers.surface,

    -- 달림 안전 기준 보정 (sidewalk/lit) — speed/surface가 확정된 뒤, classification/weights 전에
    apply_safety_tag_adjustments,

    -- handle turn lanes and road classification, used for guidance
    WayHandlers.classification,

    -- handle various other flags
    WayHandlers.roundabouts,
    WayHandlers.startpoint,

    -- set name, ref and pronunciation
    WayHandlers.names,

    -- set weight properties of the way
    WayHandlers.weights
  }

  WayHandlers.run(profile, way, result, data, handlers)
end

function process_turn (profile, turn)
  turn.duration = 0.

  if turn.direction_modifier == direction_modifier.u_turn then
     turn.duration = turn.duration + profile.properties.u_turn_penalty
  end

  if turn.has_traffic_light then
     turn.duration = profile.properties.traffic_light_penalty
  end
  if profile.properties.weight_name == 'routability' then
      -- penalize turns from non-local access only segments onto local access only tags
      if not turn.source_restricted and turn.target_restricted then
          turn.weight = turn.weight + 3000
      end
  end
end

return {
  setup = setup,
  process_way =  process_way,
  process_node = process_node,
  process_turn = process_turn
}
