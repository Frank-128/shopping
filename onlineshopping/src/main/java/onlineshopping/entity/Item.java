package onlineshopping.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import onlineshopping.constants.Categories;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "items")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Item {
    @Id
    @SequenceGenerator(
            name = "item_sequence",
            sequenceName = "item_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "item_sequence"
    )
    private Long itemId;


    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "item_number", nullable = false)
    private String itemNo;

    @Column(name = "price", nullable = false)
    private double actualPrice;

    @Column(name = "discount")
    private double discountPrice;

    @Column(name = "initial_quantity")
    private int initialQuantity;

    @Column(name = "current_quantity")
    private int currentQuantity;

    private String description;
    private int ratings;

    @Column(name = "images")
    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_sizes", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "size")
    private List<String> sizes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_colors", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "color")
    private List<String> colors;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime datePublished;

    @Enumerated(EnumType.STRING)
    private Categories category;

    @PrePersist
    public void onCreate(){
        datePublished = LocalDateTime.now().withNano(0);;
    }
}
