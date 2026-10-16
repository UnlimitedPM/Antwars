package fr.laugh.antwars.sign;

import org.bukkit.Location;

/**
 * Un panneau de connexion : sa position, et sa cible.
 *
 * <p>Cible = le nom d'une arene precise, ou vide pour un panneau
 * <b>aleatoire</b> qui envoie vers l'arene joignable la plus remplie.
 */
public class JoinSign {

    private final Location location;
    private final String target;

    public JoinSign(Location location, String target) {
        this.location = location;
        this.target = target == null ? "" : target;
    }

    public Location getLocation() {
        return location;
    }

    /** Nom de l'arene ciblee, ou chaine vide si le panneau est aleatoire. */
    public String getTarget() {
        return target;
    }

    public boolean isRandom() {
        return target.isEmpty();
    }
}
