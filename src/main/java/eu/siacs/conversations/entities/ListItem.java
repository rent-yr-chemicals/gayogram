package eu.siacs.conversations.entities;

import android.content.Context;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.xmpp.Jid;


public interface ListItem extends Comparable<ListItem>, AvatarService.Avatarable {
	String getDisplayName();

	Jid getJid();

	List<Tag> getTags(Context context);

	final class Tag implements Serializable {
		private final String name;
		private final int color;

		public Tag(final String name, final int color) {
			this.name = name;
			this.color = color;
		}

		public int getColor() {
			return this.color;
		}

		public String getName() {
			return this.name;
		}

		public String toString() {
			return getName();
		}

		public boolean equals(Object o) {
			if (!(o instanceof Tag)) return false;
			Tag ot = (Tag) o;
			return name.toLowerCase(Locale.US).equals(ot.getName().toLowerCase(Locale.US)) && color == ot.getColor();
		}

		public int hashCode() {
			return name.toLowerCase(Locale.US).hashCode();
		}
	}

	boolean match(Context context, final String needle);
}
