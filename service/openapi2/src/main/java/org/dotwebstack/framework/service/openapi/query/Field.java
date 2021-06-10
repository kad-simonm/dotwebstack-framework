package org.dotwebstack.framework.service.openapi.query;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Builder
@Data
public class Field {
  private String name;
  private Map<String, Object> arguments;
  private List<Field> children;

  public void toString(StringBuilder sb, int depth) {
    indent(sb, depth);
    sb.append(name);
    if(arguments!=null&&!arguments.isEmpty()){
      StringJoiner stringJoiner = new StringJoiner(sb);
      sb.append("(");
      arguments.forEach((key, value)->stringJoiner.add(key+": \""+value.toString()+"\""));
      sb.append(stringJoiner);
      sb.append(")");
    }
    if(children!=null&&!children.isEmpty()){
      sb.append(" {\n");
      children.forEach(c->c.toString(sb, depth+1));
      indent(sb, depth);
      sb.append("}");
    }
    sb.append("\n");
  }

  protected void indent(StringBuilder sb, int depth) {
    for(int i = 0; i< depth; i++){
      sb.append(' ');
    }
  }
}
