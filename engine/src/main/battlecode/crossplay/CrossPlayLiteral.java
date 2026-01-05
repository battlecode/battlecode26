package battlecode.crossplay;

import battlecode.common.MapLocation;
import battlecode.common.MapInfo;
import battlecode.common.Team;

import org.json.*;

public class CrossPlayLiteral extends CrossPlayObject {
    public final Object value;

    public CrossPlayLiteral(CrossPlayObjectType type, Object value) {
        super(type);
        this.value = value;
    }

    @Override
    public String toString() {
        return "CrossPlayLiteral(type=" + type + ", value=" + value + ")";
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = super.toJson();

        switch (this.type) {
            case NULL:
                json.put("value", 0);
                break;
            case BOOLEAN:
                json.put("value", (Boolean)this.value);
                break;
            case INTEGER:
                json.put("value", (Integer)this.value);
                break;
            case DOUBLE:
                json.put("value", (Double)this.value);
                break;
            case STRING:
                json.put("value", (String)this.value);
                break;
            case ARRAY:
                JSONArray jsonArr = new JSONArray();

                for (CrossPlayObject obj : (CrossPlayObject[])this.value) {
                    jsonArr.put(obj.toJson());
                }

                json.put("value", jsonArr);
                break;
            case TEAM:
                json.put("value", ((Team)this.value).ordinal());
                break;
            case MAP_LOCATION:
                MapLocation ml = (MapLocation)this.value;
                JSONObject mlJson = new JSONObject();
                mlJson.put("x", ml.x);
                mlJson.put("y", ml.y);
                json.put("value", mlJson);
                break;
            default:
                throw new CrossPlayException("Cannot encode CrossPlayObject of type " + type + " as a literal.");
        }

        return json;
    }

    public static CrossPlayLiteral fromJson(JSONObject json) {
        CrossPlayObjectType type = CrossPlayObjectType.values[json.getInt("type")];
        
        switch (type) {
            case NULL:
                return new CrossPlayLiteral(type, null);
            case BOOLEAN:
                return new CrossPlayLiteral(type, json.getBoolean("value"));
            case INTEGER:
                return new CrossPlayLiteral(type, json.getInt("value"));
            case DOUBLE:
                return new CrossPlayLiteral(type, json.getDouble("value"));
            case STRING:
                return new CrossPlayLiteral(type, json.getString("value"));
            case ARRAY:
                JSONArray jsonArr = json.getJSONArray("value");
                CrossPlayObject[] arr = new CrossPlayObject[jsonArr.length()];

                for (int i = 0; i < jsonArr.length(); i++) {
                    arr[i] = CrossPlayObject.fromJson(jsonArr.getJSONObject(i));
                }

                return new CrossPlayLiteral(type, arr);
            case TEAM:
                int ordinal = json.getInt("value");
                return new CrossPlayLiteral(type, Team.values[ordinal]);
            default:
                throw new CrossPlayException("Cannot decode CrossPlayObject of type " + type + " as a literal.");
        }
    }

    public static CrossPlayLiteral of(Object value) {
        if (value == null) {
            return NULL;
        } else if (value instanceof Boolean) {
            return ofBoolean((Boolean)value);
        } else if (value instanceof Integer) {
            return ofInt((Integer)value);
        } else if (value instanceof Double) {
            return ofDouble((Double)value);
        } else if (value instanceof String) {
            return ofString((String)value);
        } else if (value instanceof CrossPlayObject[]) {
            return ofArray((CrossPlayObject[])value);
        } else if (value instanceof Team) {
            return ofTeam((Team)value);
        } else if (value instanceof MapLocation) {
            return ofMapLocation((MapLocation)value);
        } else {
            throw new CrossPlayException("Cannot create CrossPlayLiteral from value of type " + value.getClass().getName());
        }
    }

    public static final CrossPlayLiteral
        NULL = new CrossPlayLiteral(CrossPlayObjectType.NULL, null),
        TRUE = new CrossPlayLiteral(CrossPlayObjectType.BOOLEAN, true),
        FALSE = new CrossPlayLiteral(CrossPlayObjectType.BOOLEAN, false);

    public static CrossPlayLiteral ofBoolean(Boolean value) {
        if (value == null) return NULL;
        return value ? TRUE : FALSE;
    }

    public static CrossPlayLiteral ofInt(Integer value) {
        if (value == null) return NULL;
        return new CrossPlayLiteral(CrossPlayObjectType.INTEGER, value);
    }

    public static CrossPlayLiteral ofDouble(Double value) {
        if (value == null) return NULL;
        return new CrossPlayLiteral(CrossPlayObjectType.DOUBLE, value);
    }

    public static CrossPlayLiteral ofString(String value) {
        if (value == null) return NULL;
        return new CrossPlayLiteral(CrossPlayObjectType.STRING, value);
    }

    public static CrossPlayLiteral ofArray(CrossPlayObject[] value) {
        if (value == null) return NULL;
        return new CrossPlayLiteral(CrossPlayObjectType.ARRAY, value);
    }

    public static CrossPlayLiteral ofTeam(Team value) {
        if (value == null) return NULL;
        return new CrossPlayLiteral(CrossPlayObjectType.TEAM, value);
    }

    public static CrossPlayLiteral ofMapLocation(MapLocation value) {
        if (value == null) return NULL;
        return new CrossPlayLiteral(CrossPlayObjectType.MAP_LOCATION, value);
    }

    public static CrossPlayLiteral fromMapInfo(MapInfo mi) {
        if (mi == null) return NULL;

        CrossPlayObject[] miArr = new CrossPlayObject[] {
            ofMapLocation(mi.getMapLocation()),
            ofBoolean(mi.isPassable()),
            ofBoolean(mi.isWall()),
            ofBoolean(mi.isDirt()),
            ofInt(mi.getCheeseAmount()),
            ofInt(mi.getTrap().ordinal()),
            ofBoolean(mi.hasCheeseMine())
        };

        return ofArray(miArr);
    }

    public static CrossPlayLiteral ofMapInfoArray(MapInfo[] infos) {
        if (infos == null) return ofArray(new CrossPlayObject[0]);
        CrossPlayObject[] arr = new CrossPlayObject[infos.length];

        for (int i = 0; i < infos.length; i++) {
            arr[i] = fromMapInfo(infos[i]);
        }

        return ofArray(arr);
    }
}
