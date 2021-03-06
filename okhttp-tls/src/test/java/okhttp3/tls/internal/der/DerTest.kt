/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.tls.internal.der

import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class DerTest {
  @Test fun `decode tag and length`() {
    val buffer = Buffer()
        .writeByte(0b00011110)
        .writeByte(0b10000001)
        .writeByte(0b11001001)

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_UNIVERSAL)
      assertThat(tag).isEqualTo(30)
      assertThat(constructed).isFalse()
      assertThat(length).isEqualTo(201)
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode tag and length`() {
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(tagClass = DerHeader.TAG_CLASS_UNIVERSAL, tag = 30L) {
      it.writeUtf8("a".repeat(201))
    }

    assertThat(buffer.readByteString(3)).isEqualTo("1e81c9".decodeHex())
    assertThat(buffer.readUtf8()).isEqualTo("a".repeat(201))
  }

  @Test fun `decode primitive bit string`() {
    val buffer = Buffer()
        .write("0307040A3B5F291CD0".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(3L)
      assertThat(derReader.readBitString()).isEqualTo(BitString("0A3B5F291CD0".decodeHex(), 4))
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode primitive bit string`() {
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(tagClass = DerHeader.TAG_CLASS_UNIVERSAL, tag = 3L) {
      derWriter.writeBitString(BitString("0A3B5F291CD0".decodeHex(), 4))
    }

    assertThat(buffer.readByteString()).isEqualTo("0307040A3B5F291CD0".decodeHex())
  }

  @Test fun `decode constructed bit string`() {
    val buffer = Buffer()
        .write("2380".decodeHex())
        .write("0303000A3B".decodeHex())
        .write("0305045F291CD0".decodeHex())
        .write("0000".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(3L)
      assertThat(derReader.readBitString()).isEqualTo(BitString("0A3B5F291CD0".decodeHex(), 4))
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `decode primitive string`() {
    val buffer = Buffer()
        .write("1A054A6F6E6573".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(26L)
      assertThat(constructed).isFalse()
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_UNIVERSAL)
      assertThat(derReader.readOctetString()).isEqualTo("Jones".encodeUtf8())
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode primitive string`() {
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(tagClass = DerHeader.TAG_CLASS_UNIVERSAL, tag = 26L) {
      derWriter.writeOctetString("Jones".encodeUtf8())
    }

    assertThat(buffer.readByteString()).isEqualTo("1A054A6F6E6573".decodeHex())
  }

  @Test fun `decode constructed string`() {
    val buffer = Buffer()
        .write("3A0904034A6F6E04026573".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(26L)
      assertThat(constructed).isTrue()
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_UNIVERSAL)
      assertThat(derReader.readOctetString()).isEqualTo("Jones".encodeUtf8())
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `decode implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    val buffer = Buffer()
        .write("43054A6F6E6573".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(3L)
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_APPLICATION)
      assertThat(derReader.readOctetString()).isEqualTo("Jones".encodeUtf8())
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(tagClass = DerHeader.TAG_CLASS_APPLICATION, tag = 3L) {
      derWriter.writeOctetString("Jones".encodeUtf8())
    }

    assertThat(buffer.readByteString()).isEqualTo("43054A6F6E6573".decodeHex())
  }

  @Test fun `decode tagged implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    // Type3 ::= [2] Type2
    val buffer = Buffer()
        .write("A20743054A6F6E6573".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(2L)
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_CONTEXT_SPECIFIC)
      assertThat(length).isEqualTo(7L)

      derReader.read(derAdapter { tagClass, tag, constructed, length ->
        assertThat(tag).isEqualTo(3L)
        assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_APPLICATION)
        assertThat(length).isEqualTo(5L)
        assertThat(derReader.readOctetString()).isEqualTo("Jones".encodeUtf8())
      })

      assertThat(derReader.hasNext()).isFalse()
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode tagged implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    // Type3 ::= [2] Type2
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(tagClass = DerHeader.TAG_CLASS_CONTEXT_SPECIFIC, tag = 2L) {
      derWriter.value(tagClass = DerHeader.TAG_CLASS_APPLICATION, tag = 3L) {
        derWriter.writeOctetString("Jones".encodeUtf8())
      }
    }

    assertThat(buffer.readByteString()).isEqualTo("A20743054A6F6E6573".decodeHex())
  }

  @Test fun `decode implicit tagged implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    // Type3 ::= [2] Type2
    // Type4 ::= [APPLICATION 7] IMPLICIT Type3
    val buffer = Buffer()
        .write("670743054A6F6E6573".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(7L)
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_APPLICATION)
      assertThat(length).isEqualTo(7L)

      derReader.read(derAdapter { tagClass, tag, constructed, length ->
        assertThat(tag).isEqualTo(3L)
        assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_APPLICATION)
        assertThat(length).isEqualTo(5L)
        assertThat(derReader.readOctetString()).isEqualTo("Jones".encodeUtf8())
      })

      assertThat(derReader.hasNext()).isFalse()
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode implicit tagged implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    // Type3 ::= [2] Type2
    // Type4 ::= [APPLICATION 7] IMPLICIT Type3
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(tagClass = DerHeader.TAG_CLASS_APPLICATION, tag = 7L) {
      derWriter.value(tagClass = DerHeader.TAG_CLASS_APPLICATION, tag = 3L) {
        derWriter.writeOctetString("Jones".encodeUtf8())
      }
    }

    assertThat(buffer.readByteString()).isEqualTo("670743054A6F6E6573".decodeHex())
  }

  @Test fun `decode implicit implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    // Type5 ::= [2] IMPLICIT Type2
    val buffer = Buffer()
        .write("82054A6F6E6573".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(2L)
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_CONTEXT_SPECIFIC)
      assertThat(length).isEqualTo(5L)
      assertThat(derReader.readOctetString()).isEqualTo("Jones".encodeUtf8())
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode implicit implicit prefixed type`() {
    // Type1 ::= VisibleString
    // Type2 ::= [APPLICATION 3] IMPLICIT Type1
    // Type5 ::= [2] IMPLICIT Type2
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(
        tagClass = DerHeader.TAG_CLASS_CONTEXT_SPECIFIC,
        tag = 2L
    ) {
      derWriter.writeOctetString("Jones".encodeUtf8())
    }

    assertThat(buffer.readByteString()).isEqualTo("82054A6F6E6573".decodeHex())
  }

  @Test fun `decode object identifier without adapter`() {
    val buffer = Buffer()
        .write("0603883703".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(6L)
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_UNIVERSAL)
      assertThat(length).isEqualTo(3L)
      assertThat(derReader.readObjectIdentifier()).isEqualTo("2.999.3")
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode object identifier without adapter`() {
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(
        tagClass = DerHeader.TAG_CLASS_UNIVERSAL,
        tag = 6L
    ) {
      derWriter.writeObjectIdentifier("2.999.3")
    }

    assertThat(buffer.readByteString()).isEqualTo("0603883703".decodeHex())
  }

  @Test fun `decode relative object identifier`() {
    val buffer = Buffer()
        .write("0D04c27B0302".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(13L)
      assertThat(tagClass).isEqualTo(DerHeader.TAG_CLASS_UNIVERSAL)
      assertThat(length).isEqualTo(4L)
      assertThat(derReader.readRelativeObjectIdentifier()).isEqualTo("8571.3.2")
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode relative object identifier`() {
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(
        tagClass = DerHeader.TAG_CLASS_UNIVERSAL,
        tag = 13L
    ) {
      derWriter.writeRelativeObjectIdentifier("8571.3.2")
    }

    assertThat(buffer.readByteString()).isEqualTo("0D04c27B0302".decodeHex())
  }

  @Test fun `decode raw sequence`() {
    val buffer = Buffer()
        .write("300A".decodeHex())
        .write("1505".decodeHex())
        .write("Smith".encodeUtf8())
        .write("01".decodeHex())
        .write("01".decodeHex())
        .write("FF".decodeHex())

    val derReader = DerReader(buffer)

    derReader.read(derAdapter { tagClass, tag, constructed, length ->
      assertThat(tag).isEqualTo(16L)

      derReader.read(derAdapter { tagClass, tag, constructed, length ->
        assertThat(tag).isEqualTo(21L)
        assertThat(derReader.readOctetString()).isEqualTo("Smith".encodeUtf8())
      })

      derReader.read(derAdapter { tagClass, tag, constructed, length ->
        assertThat(tag).isEqualTo(1L)
        assertThat(derReader.readBoolean()).isTrue()
      })

      assertThat(derReader.hasNext()).isFalse()
    })

    assertThat(derReader.hasNext()).isFalse()
  }

  @Test fun `encode raw sequence`() {
    val buffer = Buffer()
    val derWriter = DerWriter(buffer)

    derWriter.value(
        tagClass = DerHeader.TAG_CLASS_UNIVERSAL,
        tag = 16L
    ) {

      derWriter.value(
          tagClass = DerHeader.TAG_CLASS_UNIVERSAL,
          tag = 21L
      ) {
        derWriter.writeOctetString("Smith".encodeUtf8())
      }

      derWriter.value(
          tagClass = DerHeader.TAG_CLASS_UNIVERSAL,
          tag = 1L
      ) {
        derWriter.writeBoolean(true)
      }
    }

    assertThat(buffer.readByteString()).isEqualTo("300a1505536d6974680101ff".decodeHex())
  }

  @Test fun `decode sequence of`() {
    val list = Adapters.INTEGER_AS_LONG
        .asSequenceOf()
        .fromDer("3009020107020108020109".decodeHex())
    assertThat(list).containsExactly(7L, 8L, 9L)
  }

  @Test fun `encode sequence of`() {
    val byteString = Adapters.INTEGER_AS_LONG
        .asSequenceOf()
        .toDer(listOf(7L, 8L, 9L))
    assertThat(byteString).isEqualTo("3009020107020108020109".decodeHex())
  }

  @Test fun `decode point with only x set`() {
    val point = Point.ADAPTER.fromDer("3003800109".decodeHex())
    assertThat(point).isEqualTo(Point(9L, null))
  }

  @Test fun `encode point with only x set`() {
    val point = Point.ADAPTER.fromDer("3003800109".decodeHex())
    assertThat(point).isEqualTo(Point(9L, null))
  }

  @Test fun `decode point with only y set`() {
    val point = Point.ADAPTER.fromDer("3003810109".decodeHex())
    assertThat(point).isEqualTo(Point(null, 9L))
  }

  @Test fun `encode point with only y set`() {
    val point = Point.ADAPTER.fromDer("3003810109".decodeHex())
    assertThat(point).isEqualTo(Point(null, 9L))
  }

  @Test fun `decode point with both fields set`() {
    val point = Point.ADAPTER.fromDer("3006800109810109".decodeHex())
    assertThat(point).isEqualTo(Point(9L, 9L))
  }

  @Test fun `encode point with both fields set`() {
    val point = Point.ADAPTER.fromDer("3006800109810109".decodeHex())
    assertThat(point).isEqualTo(Point(9L, 9L))
  }

  @Test fun `decode implicit`() {
    // [5] IMPLICIT UTF8String
    val implicitAdapter = Adapters.UTF8_STRING.withTag(tag = 5L)
    val string = implicitAdapter.fromDer("85026869".decodeHex())
    assertThat(string).isEqualTo("hi")
  }

  @Test fun `encode implicit`() {
    // [5] IMPLICIT UTF8String
    val implicitAdapter = Adapters.UTF8_STRING.withTag(tag = 5L)
    val string = implicitAdapter.fromDer("85026869".decodeHex())
    assertThat(string).isEqualTo("hi")
  }

  @Test fun `decode explicit`() {
    // [5] EXPLICIT UTF8String
    val explicitAdapter = Adapters.UTF8_STRING.withExplicitBox(tag = 5L)
    val string = explicitAdapter.fromDer("A5040C026869".decodeHex())
    assertThat(string).isEqualTo("hi")
  }

  @Test fun `encode explicit`() {
    // [5] EXPLICIT UTF8String
    val explicitAdapter = Adapters.UTF8_STRING.withExplicitBox(tag = 5L)
    val string = explicitAdapter.fromDer("A5040C026869".decodeHex())
    assertThat(string).isEqualTo("hi")
  }

  @Test fun `decode boolean`() {
    val boolean = Adapters.BOOLEAN.fromDer("0101FF".decodeHex())
    assertThat(boolean).isEqualTo(true)
  }

  @Test fun `encode boolean`() {
    val byteString = Adapters.BOOLEAN.toDer(true)
    assertThat(byteString).isEqualTo("0101FF".decodeHex())
  }

  @Test fun `decode positive integer`() {
    val byteString = Adapters.INTEGER_AS_LONG.fromDer("020132".decodeHex())
    assertThat(byteString).isEqualTo(50L)
  }

  @Test fun `encode positive integer`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(50L)
    assertThat(byteString).isEqualTo("020132".decodeHex())
  }

  @Test fun `decode negative integer`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("02019c".decodeHex())
    assertThat(integer).isEqualTo(-100L)
  }

  @Test fun `encode negative integer`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(-100L)
    assertThat(byteString).isEqualTo("02019c".decodeHex())
  }

  @Test fun `decode five byte integer`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("02058000000001".decodeHex())
    assertThat(integer).isEqualTo(-549755813887L)
  }

  @Test fun `encode five byte integer`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(-549755813887L)
    assertThat(byteString).isEqualTo("02058000000001".decodeHex())
  }

  @Test fun `decode with eight zeros`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("020200ff".decodeHex())
    assertThat(integer).isEqualTo(255)
  }

  @Test fun `encode with eight zeros`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(255)
    assertThat(byteString).isEqualTo("020200ff".decodeHex())
  }

  @Test fun `encode with eight ones`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(-1L)
    assertThat(byteString).isEqualTo("0201ff".decodeHex())
  }

  @Test fun `decode with eight ones`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("0201ff".decodeHex())
    assertThat(integer).isEqualTo(-1L)
  }

  @Test fun `encode last byte all zeros`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(-256L)
    assertThat(byteString).isEqualTo("0202ff00".decodeHex())
  }

  @Test fun `decode last byte all zeros`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("0202ff00".decodeHex())
    assertThat(integer).isEqualTo(-256L)
  }

  @Test fun `decode max long`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("02087fffffffffffffff".decodeHex())
    assertThat(integer).isEqualTo(Long.MAX_VALUE)
  }

  @Test fun `encode max long`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(Long.MAX_VALUE)
    assertThat(byteString).isEqualTo("02087fffffffffffffff".decodeHex())
  }

  @Test fun `decode min long`() {
    val integer = Adapters.INTEGER_AS_LONG.fromDer("02088000000000000000".decodeHex())
    assertThat(integer).isEqualTo(Long.MIN_VALUE)
  }

  @Test fun `encode min long`() {
    val byteString = Adapters.INTEGER_AS_LONG.toDer(Long.MIN_VALUE)
    assertThat(byteString).isEqualTo("02088000000000000000".decodeHex())
  }

  @Test fun `decode bigger than max long`() {
    val bigInteger = Adapters.INTEGER_AS_BIG_INTEGER.fromDer("0209008000000000000001".decodeHex())
    assertThat(bigInteger).isEqualTo(BigInteger("9223372036854775809"))
  }

  @Test fun `encode bigger than max long`() {
    val byteString = Adapters.INTEGER_AS_BIG_INTEGER.toDer(BigInteger("9223372036854775809"))
    assertThat(byteString).isEqualTo("0209008000000000000001".decodeHex())
  }

  @Test fun `decode utf8 string`() {
    val string = Adapters.UTF8_STRING.fromDer("0c04f09f988e".decodeHex())
    assertThat(string).isEqualTo("\uD83D\uDE0E")
  }

  @Test fun `encode utf8 string`() {
    val byteString = Adapters.UTF8_STRING.toDer("\uD83D\uDE0E")
    assertThat(byteString).isEqualTo("0c04f09f988e".decodeHex())
  }

  @Test fun `decode ia5`() {
    val string = Adapters.IA5_STRING.fromDer("16026869".decodeHex())
    assertThat(string).isEqualTo("hi")
  }

  @Test fun `encode ia5`() {
    val byteString = Adapters.IA5_STRING.toDer("hi")
    assertThat(byteString).isEqualTo("16026869".decodeHex())
  }

  @Test fun `decode printable string`() {
    val string = Adapters.PRINTABLE_STRING.fromDer("13026869".decodeHex())
    assertThat(string).isEqualTo("hi")
  }

  @Test fun `encode printable string`() {
    val byteString = Adapters.PRINTABLE_STRING.toDer("hi")
    assertThat(byteString).isEqualTo("13026869".decodeHex())
  }

  @Test fun `decode utc time`() {
    val time = Adapters.UTC_TIME.fromDer("17113139313231353139303231302d30383030".decodeHex())
    assertThat(time).isEqualTo(date("2019-12-16T03:02:10.000+0000").time)
  }

  @Test fun `encode utc time`() {
    val byteString = Adapters.UTC_TIME.toDer(date("2019-12-16T03:02:10.000+0000").time)
    assertThat(byteString).isEqualTo("170d3139313231363033303231305a".decodeHex())
  }

  @Test fun `decode generalized time`() {
    val time = Adapters.GENERALIZED_TIME
        .fromDer("181332303139313231353139303231302d30383030".decodeHex())
    assertThat(time).isEqualTo(date("2019-12-16T03:02:10.000+0000").time)
  }

  @Test fun `encode generalized time`() {
    val byteString = Adapters.GENERALIZED_TIME.toDer(date("2019-12-16T03:02:10.000+0000").time)
    assertThat(byteString).isEqualTo("180f32303139313231363033303231305a".decodeHex())
  }

  @Test fun `decode utc time two digit year cutoff is 1950`() {
    assertThat(Adapters.parseUtcTime("500101000000-0000"))
        .isEqualTo(date("1950-01-01T00:00:00.000+0000").time)
    assertThat(Adapters.parseUtcTime("500101000000-0100"))
        .isEqualTo(date("1950-01-01T01:00:00.000+0000").time)

    assertThat(Adapters.parseUtcTime("491231235959+0100"))
        .isEqualTo(date("2049-12-31T22:59:59.000+0000").time)
    assertThat(Adapters.parseUtcTime("491231235959-0000"))
        .isEqualTo(date("2049-12-31T23:59:59.000+0000").time)

    // Note that time zone offsets aren't honored by Java's two-digit offset boundary! A savvy time
    // traveler could exploit this to get a certificate that expires 100 years later than expected.
    assertThat(Adapters.parseUtcTime("500101000000+0100"))
        .isEqualTo(date("2049-12-31T23:00:00.000+0000").time)
    assertThat(Adapters.parseUtcTime("491231235959-0100"))
        .isEqualTo(date("2050-01-01T00:59:59.000+0000").time)
  }

  @Test fun `encode utc time two digit year cutoff is 1950`() {
    assertThat(Adapters.formatUtcTime(date("1950-01-01T00:00:00.000+0000").time))
        .isEqualTo("500101000000Z")
    assertThat(Adapters.formatUtcTime(date("2049-12-31T23:59:59.000+0000").time))
        .isEqualTo("491231235959Z")
  }

  @Test fun `parse generalized time`() {
    assertThat(Adapters.parseGeneralizedTime("18990101000000-0000"))
        .isEqualTo(date("1899-01-01T00:00:00.000+0000").time)
    assertThat(Adapters.parseGeneralizedTime("19500101000000-0000"))
        .isEqualTo(date("1950-01-01T00:00:00.000+0000").time)
    assertThat(Adapters.parseGeneralizedTime("20500101000000-0000"))
        .isEqualTo(date("2050-01-01T00:00:00.000+0000").time)
    assertThat(Adapters.parseGeneralizedTime("20990101000000-0000"))
        .isEqualTo(date("2099-01-01T00:00:00.000+0000").time)
  }

  @Test fun `format generalized time`() {
    assertThat(Adapters.formatGeneralizedTime(date("1899-01-01T00:00:00.000+0000").time))
        .isEqualTo("18990101000000Z")
    assertThat(Adapters.formatGeneralizedTime(date("1950-01-01T00:00:00.000+0000").time))
        .isEqualTo("19500101000000Z")
    assertThat(Adapters.formatGeneralizedTime(date("2050-01-01T00:00:00.000+0000").time))
        .isEqualTo("20500101000000Z")
    assertThat(Adapters.formatGeneralizedTime(date("2099-01-01T00:00:00.000+0000").time))
        .isEqualTo("20990101000000Z")
  }

  @Test fun `decode object identifier`() {
    val objectIdentifier = Adapters.OBJECT_IDENTIFIER.fromDer("06092a864886f70d01010b".decodeHex())
    assertThat(objectIdentifier).isEqualTo("1.2.840.113549.1.1.11")
  }

  @Test fun `encode object identifier`() {
    val byteString = Adapters.OBJECT_IDENTIFIER.toDer("1.2.840.113549.1.1.11")
    assertThat(byteString).isEqualTo("06092a864886f70d01010b".decodeHex())
  }

  @Test fun `decode null`() {
    val unit = Adapters.NULL.fromDer("0500".decodeHex())
    assertThat(unit).isNull()
  }

  @Test fun `encode null`() {
    val byteString = Adapters.NULL.toDer(null)
    assertThat(byteString).isEqualTo("0500".decodeHex())
  }

  @Test fun `decode sequence algorithm`() {
    val algorithmIdentifier = CertificateAdapters.algorithmIdentifier
        .fromDer("300d06092a864886f70d01010b0500".decodeHex())
    assertThat(algorithmIdentifier).isEqualTo(
        AlgorithmIdentifier(
            algorithm = "1.2.840.113549.1.1.11",
            parameters = null
        )
    )
  }

  @Test fun `encode sequence algorithm`() {
    val byteString = CertificateAdapters.algorithmIdentifier.toDer(
        AlgorithmIdentifier(
            algorithm = "1.2.840.113549.1.1.11",
            parameters = null
        )
    )

    // Note that the parameters value is omitted from the output because it is optional.
    assertThat(byteString).isEqualTo("300b06092a864886f70d01010b".decodeHex())
  }

  @Test fun `decode bit string`() {
    val bitString = Adapters.BIT_STRING.fromDer("0304066e5dc0".decodeHex())
    assertThat(bitString).isEqualTo(BitString("6e5dc0".decodeHex(), 6))
  }

  @Test fun `encode bit string`() {
    val byteString = Adapters.BIT_STRING.toDer(BitString("6e5dc0".decodeHex(), 6))
    assertThat(byteString).isEqualTo("0304066e5dc0".decodeHex())
  }

  @Test fun `decode octet string`() {
    val octetString = Adapters.OCTET_STRING.fromDer("0404030206A0".decodeHex())
    assertThat(octetString).isEqualTo("030206A0".decodeHex())
  }

  @Test fun `encode octet string`() {
    val byteString = Adapters.OCTET_STRING.toDer("030206A0".decodeHex())
    assertThat(byteString).isEqualTo("0404030206A0".decodeHex())
  }

  @Test fun `decode choice rfc822`() {
    val rfc822 = GeneralName.ADAPTER.fromDer("810d61406578616d706c652e636f6d".decodeHex())
    assertThat(rfc822).isEqualTo(GeneralName.rfc822Name to "a@example.com")
  }

  @Test fun `encode choice rfc822`() {
    val byteString = GeneralName.ADAPTER.toDer(GeneralName.rfc822Name to "a@example.com")
    assertThat(byteString).isEqualTo("810d61406578616d706c652e636f6d".decodeHex())
  }

  @Test fun `decode choice dns`() {
    val dns = GeneralName.ADAPTER.fromDer("820b6578616d706c652e636f6d".decodeHex())
    assertThat(dns).isEqualTo(GeneralName.dNSName to "example.com")
  }

  @Test fun `encode choice dns`() {
    val byteString = GeneralName.ADAPTER.toDer(GeneralName.dNSName to "example.com")
    assertThat(byteString).isEqualTo("820b6578616d706c652e636f6d".decodeHex())
  }

  private fun derAdapter(block: (Int, Long, Boolean, Long) -> Unit): DerAdapter<Unit> {
    return object : DerAdapter<Unit>(-1, -1L) {
      override fun encode(writer: DerWriter, value: Unit): Unit = throw error("unsupported")

      override fun decode(reader: DerReader, header: DerHeader) {
        return block(header.tagClass, header.tag, header.constructed, header.length)
      }
    }
  }

  private fun DerWriter.value(tagClass: Int, tag: Long, block: (DerWriter) -> Unit) {
    return write(object : DerAdapter<Unit?>(tagClass, tag) {
      override fun encode(writer: DerWriter, value: Unit?) {
        block(writer)
      }

      override fun decode(reader: DerReader, header: DerHeader): Unit? = throw error("unsupported")
    }, null)
  }

  /**
   * ```
   * GeneralName ::= CHOICE {
   *   otherName                       [0]     OtherName,
   *   rfc822Name                      [1]     IA5String,
   *   dNSName                         [2]     IA5String,
   *   x400Address                     [3]     ORAddress,
   *   directoryName                   [4]     Name,
   *   ediPartyName                    [5]     EDIPartyName,
   *   uniformResourceIdentifier       [6]     IA5String,
   *   iPAddress                       [7]     OCTET STRING,
   *   registeredID                    [8]     OBJECT IDENTIFIER
   * }
   * ```
   */
  object GeneralName {
    val rfc822Name = Adapters.IA5_STRING.withTag(tag = 1L)
    val dNSName = Adapters.IA5_STRING.withTag(tag = 2L)
    val uniformResourceIdentifier = Adapters.IA5_STRING.withTag(tag = 6L)
    val iPAddress = Adapters.OCTET_STRING.withTag(tag = 7L)
    val registeredID = Adapters.OBJECT_IDENTIFIER.withTag(tag = 8L)
    val ADAPTER = Adapters.choice(
        rfc822Name,
        dNSName,
        uniformResourceIdentifier,
        iPAddress,
        registeredID
    )
  }

  /**
   * ```
   * Point ::= SEQUENCE {
   *   x [0] INTEGER OPTIONAL,
   *   y [1] INTEGER OPTIONAL
   * }
   * ```
   */
  data class Point(
    val x: Long?,
    val y: Long?
  ) {
    companion object {
      val ADAPTER = Adapters.sequence(
          Adapters.INTEGER_AS_LONG.withTag(tag = 0L).optional(),
          Adapters.INTEGER_AS_LONG.withTag(tag = 1L).optional(),
          decompose = { listOf(it.x, it.y) },
          construct = { Point(it[0] as Long?, it[1] as Long?) }
      )
    }
  }

  private fun date(s: String): Date {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").apply {
      timeZone = TimeZone.getTimeZone("GMT")
    }
    return format.parse(s)
  }
}
