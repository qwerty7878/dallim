-- 달림(Dallim) 모양 선택(SHAPE) AI 생성 전용 보행자(foot) 프로필 — dallim-foot.lua 베이스,
-- 배율 상수만 조정 (다른 access/handler 설정은 그대로 재사용).
--
-- 배경 (docs/02-api-spec.md 13.1/13.2):
-- dallim-foot.lua(8장/11장 LOOP/POINT_TO_POINT용)는 footway/pedestrian/path 같은 인도·공원
-- 보행로를 항상 최우대(1.2배)하고, sidewalk=yes인 큰길은 "중립"(1.0배)까지만 올려준다 —
-- 즉 인도가 있는 대로변이라도 공원길보다는 항상 못하다. SHAPE 모드는 반대로 "도로보다 도보
-- 위주의 대로변"을 우선하고 싶다는 요청이라, 다음처럼 뒤집는다:
--   - 인도/공원 보행로: 최우대(1.2) -> 중립(1.0)으로 낮춤
--   - 인도 있는 대로(primary/secondary/tertiary + sidewalk=yes): 중립(1.0) -> 신규 최우대(1.3)
--   - 이면도로(residential/unclassified): 중립(1.0) -> 약한 기피(0.8)
--   - 인도 없는/불명 대로(primary/trunk): 강한 기피(0.35) -> 완화(0.5)
--   - lit=no 페널티(0.85)는 그대로
--
-- 배율은 dallim-foot.lua가 처음 그랬듯 1단계 추정치다 — 실제 서비스 벨트 좌표에 돌려보고
-- 재조정하는 게 이 문서(13.2)가 명시한 구현 단계 첫 작업이다.
--
-- 데이터셋도 다르다: 전국(south-korea.osrm)이 아니라 scripts/osrm-shape-build.sh가
-- osmium extract로 잘라낸 서비스 벨트(안양·군포·의왕·과천·성남 분당/판교)만 담은
-- dallim-belt.osrm — Lightsail t3.micro(RAM 1GB)에 전국 데이터셋을 두 벌 못 올린다(13.2).

api_version = 2

Set = require('lib/set')
Sequence = require('lib/sequence')
Handlers = require("lib/way_handlers")
find_access_tag = require("lib/access").find_access_tag

local walking_speed = 5

-- SHAPE 프로필 가중치 배율 (walking_speed 대비) — dallim-foot.lua의 상수와 1:1 대응, 값만 조정.
local PARK_PATH_MULTIPLIER             = 1.0   -- 인도/공원 보행로/보행자 전용도로 (기존 1.2 -> 중립으로 낮춤)
local NEUTRAL_MULTIPLIER               = 1.0   -- 판단 보류 — 기존과 동일
local SIDE_STREET_MULTIPLIER           = 0.8   -- 이면도로(residential/unclassified) — 기존 1.0(중립) -> 약한 기피
local DISCOURAGED_MULTIPLIER           = 0.55  -- 차량 통행 많고 인도 없을 가능성이 높은 중간 규모 도로 (기존과 동일)
local STRONGLY_DISCOURAGED_MULTIPLIER  = 0.5   -- 간선/고속 성격의 큰길, 인도 없음/불명 (기존 0.35 -> 완화)
local SIDEWALK_YES_OVERRIDE_MULTIPLIER = 1.3   -- sidewalk 명시 시 큰길 신규 최우대 (기존 1.0(중립) -> 1.3)
local SIDEWALK_NO_PENALTY              = 0.7   -- sidewalk=no/none 명시 시 추가 페널티 (기존과 동일)
local UNLIT_PENALTY                    = 0.85  -- lit=no(조명 없음) 추가 페널티 (기존과 동일)

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

    -- 도로 유형별 기본 speed — dallim-foot.lua와 동일 구조, SHAPE용 배율만 적용(위 상수 참고).
    speeds = Sequence {
      highway = {
        -- 중립으로 낮춤: 인도, 공원·하천변 산책로, 보행자 전용 (SHAPE는 대로변을 우선한다)
        footway         = walking_speed * PARK_PATH_MULTIPLIER,
        pedestrian      = walking_speed * PARK_PATH_MULTIPLIER,
        path            = walking_speed * PARK_PATH_MULTIPLIER,
        living_street   = walking_speed * PARK_PATH_MULTIPLIER,
        track           = walking_speed * PARK_PATH_MULTIPLIER,
        steps           = walking_speed * NEUTRAL_MULTIPLIER,
        pier            = walking_speed * NEUTRAL_MULTIPLIER,

        -- 약한 기피: 이면도로 — sidewalk/lit 태그가 있으면 아래에서 추가 보정
        residential     = walking_speed * SIDE_STREET_MULTIPLIER,
        unclassified    = walking_speed * SIDE_STREET_MULTIPLIER,
        road            = walking_speed * NEUTRAL_MULTIPLIER,
        service         = walking_speed * NEUTRAL_MULTIPLIER,

        -- 기피: 차량 통행 많고 인도 없을 가능성이 높은 중간 규모 도로
        tertiary        = walking_speed * DISCOURAGED_MULTIPLIER,
        tertiary_link   = walking_speed * DISCOURAGED_MULTIPLIER,
        secondary       = walking_speed * DISCOURAGED_MULTIPLIER,
        secondary_link  = walking_speed * DISCOURAGED_MULTIPLIER,

        -- 완화된 기피: 간선/고속 성격의 큰길 (인도 있으면 아래에서 최우대로 보정)
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
        track           = walking_speed * PARK_PATH_MULTIPLIER -- 공원 내 산책/운동 트랙
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

-- sidewalk/lit 태그 기반 추가 보정 — dallim-foot.lua와 동일 구조, SHAPE용 배율만 적용.
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

  -- sidewalk=* : 인도 유무가 명시된 경우, 큰길 배율을 보정한다.
  local sidewalk = way:get_value_by_key('sidewalk')
  if sidewalk then
    if BIG_ROAD_HIGHWAYS[highway] and
       (sidewalk == 'both' or sidewalk == 'left' or sidewalk == 'right' or
        sidewalk == 'yes' or sidewalk == 'separate') then
      -- 인도가 있다고 명시된 큰길 — SHAPE 모드는 이걸 신규 최우대로 끌어올린다.
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

    -- 달림 SHAPE 안전 기준 보정 (sidewalk/lit) — speed/surface가 확정된 뒤, classification/weights 전에
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
