package pro.eng.yui.android.osmjppostalmap.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Function;

import pro.eng.yui.oss.osm.lib.jppostalcore.types.JpAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AddressEditDialogTest {

    @SuppressWarnings("unchecked")
    @Test
    public void roomFieldUsesAddrUnitAndJpAddressUnitAccessors() throws Exception {
        Class<?> fieldType = Class.forName(AddressEditDialog.class.getName() + "$Field");
        Field roomConstantField = fieldType.getDeclaredField("ROOM");
        roomConstantField.setAccessible(true);
        Object roomField = roomConstantField.get(null);

        Field tagKeyField = fieldType.getDeclaredField("tagKey");
        Field getterField = fieldType.getDeclaredField("getter");
        Field setterField = fieldType.getDeclaredField("setter");
        tagKeyField.setAccessible(true);
        getterField.setAccessible(true);
        setterField.setAccessible(true);

        assertEquals("addr:unit", tagKeyField.get(roomField));

        Function<JpAddress, String> getter = (Function<JpAddress, String>) getterField.get(roomField);
        BiConsumer<JpAddress, String> setter = (BiConsumer<JpAddress, String>) setterField.get(roomField);
        assertNotNull(getter);
        assertNotNull(setter);
        JpAddress address = new JpAddress();

        setter.accept(address, "302");
        assertEquals("302", getter.apply(address));
        assertEquals("302", address.getUnit());

        setter.accept(address, "");
        assertEquals("", getter.apply(address));
        setter.accept(address, null);
        assertNull(address.getUnit());
    }
}
