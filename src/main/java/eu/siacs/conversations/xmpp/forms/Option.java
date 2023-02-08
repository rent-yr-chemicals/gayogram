package eu.siacs.conversations.xmpp.forms;

import java.util.ArrayList;
import java.util.List;
import eu.siacs.conversations.xml.Element;

public class Option {
    protected final String value;
    protected final String label;

    public static List<Option> forField(Element field) {
        List<Option> options = new ArrayList<>();
        for (Element el : field.getChildren()) {
            if (!el.getNamespace().equals("jabber:x:data")) continue;
            if (!el.getName().equals("option")) continue;
            options.add(new Option(el));
        }
        return options;
    }

    public Option(final Element option) {
        this(option.findChildContent("value", "jabber:x:data"), option.getAttribute("label"));
    }

    public Option(final String value, final String label) {
        this.value = value;
        this.label = label == null ? value : label;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Option)) return false;

        if (value == ((Option) o).value) return true;
        if (value == null || ((Option) o).value == null) return false;
        return value.equals(((Option) o).value);
    }

    public String toString() { return label; }

    public String getValue() { return value; }
}
