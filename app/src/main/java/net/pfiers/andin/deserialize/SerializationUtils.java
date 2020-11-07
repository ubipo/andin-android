package net.pfiers.andin.deserialize;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Utils for (de)serialization into Base64-encoded string for future persisting.
 *
 * @author andy
 */
public class SerializationUtils {

    public static <T extends Serializable> String serialize(T item) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectOutputStream;
        try {
            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(item);
            objectOutputStream.close();
            return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static <T extends Serializable> T deserialize(String data) {
        try {
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dataBytes);
            final ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

            @SuppressWarnings({"unchecked"})
            final T obj = (T) objectInputStream.readObject();

            objectInputStream.close();
            return obj;
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
    }


}
