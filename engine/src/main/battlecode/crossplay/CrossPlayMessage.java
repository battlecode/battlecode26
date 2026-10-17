package battlecode.crossplay;

import com.fasterxml.jackson.databind.JsonNode;

public record CrossPlayMessage(CrossPlayMethod method, JsonNode params) {}
