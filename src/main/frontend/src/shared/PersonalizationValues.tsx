import { Column, Image, Row, Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent } from "@tiendanube/nube-sdk-types";
import type { NamedProperty } from "./properties";

export function PersonalizationValues({ fields, color }: { fields: NamedProperty[]; color?: string }) {
    return <Column gap={6}>
        {fields.map(field => {
            const children: NubeComponent[] = [];
            if (field.thumbnailUrl) children.push(<Image src={field.thumbnailUrl} alt={`${field.name}: ${field.value}`}
                width="56px" height="56px" style={{ objectFit: "contain", borderRadius: "6px" }} />);
            children.push(<Text color={color} modifiers={["bold"]} style={{ fontSize: "14px", lineHeight: "18px" }}>
                {`${field.name}: ${field.value}`}
            </Text>);
            return <Row gap={8} alignItems="center">{children}</Row>;
        })}
    </Column>;
}
