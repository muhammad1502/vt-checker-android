package com.ninpo.vtchecker;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Objects;

/** Something VirusTotal can look up: a link, a domain, an IP address or a file hash. */
public final class Indicator {

    public enum Type { URL, DOMAIN, IP_ADDRESS, FILE_HASH }

    @NonNull public final Type type;
    @NonNull public final String value;

    Indicator(@NonNull Type type, @NonNull String value) {
        this.type = type;
        this.value = value;
    }

    /** Short label for the UI, such as "Link" or "SHA-256 file hash". */
    @StringRes
    public int label() {
        switch (type) {
            case URL:
                return R.string.type_url;
            case DOMAIN:
                return R.string.type_domain;
            case IP_ADDRESS:
                return R.string.type_ip;
            case FILE_HASH:
            default:
                if (value.length() == 32) return R.string.type_md5;
                if (value.length() == 40) return R.string.type_sha1;
                return R.string.type_sha256;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Indicator)) return false;
        Indicator other = (Indicator) o;
        return type == other.type && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @NonNull
    @Override
    public String toString() {
        return type + ":" + value;
    }
}
