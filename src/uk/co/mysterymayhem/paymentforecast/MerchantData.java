package uk.co.mysterymayhem.paymentforecast;

import java.util.Objects;

/**
 * Data that must be constant for each merchant.
 * This assumes that a specific id has a constant name and public key
 * Created by Mysteryem on 25/04/2017.
 */
public class MerchantData {
    // Going to be working with Integer objects, so may as well reduce the amount of auto(un)boxing
    public final Integer id;
    public final String name;
    public final String publicKey;

    public MerchantData(Integer id, String name, String publicKey) {
        this.id = id;
        this.name = name;
        this.publicKey = publicKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MerchantData) {
            MerchantData other = (MerchantData) obj;
            return this.id.equals(other.id) && this.name.equals(other.name) && this.publicKey.equals(other.publicKey);
        }
        return false;
    }

    /**
     * Convenience method for comparisons prior to constructing a new instance. This should be a minimal timesave where
     * it is not necessary to construct a new MerchantData beforehand.
     *
     * @param id
     * @param name
     * @param publicKey
     * @return
     */
    public boolean matches(Integer id, String name, String publicKey) {
        return this.id.equals(id) && this.name.equals(name) && this.publicKey.equals(publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.publicKey);
    }

    @Override
    public String toString() {
        return "ID: " + this.id + ", Name: " + this.name + ", PubKey: " + publicKey;
    }
}
